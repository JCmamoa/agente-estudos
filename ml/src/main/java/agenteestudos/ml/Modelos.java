package agenteestudos.ml;

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.loss.Loss;
import ai.djl.translate.NoopTranslator;

import java.io.IOException;
import java.util.Random;

/**
 * Os dois modelos do módulo ML, cada num engine (escopo escolhido):
 * TensorFlow → carga sustentável por dia (regressão linear treinada direto
 *              com tensores TF; o engine DJL de TF não suporta Block/Trainer).
 * PyTorch    → risco de atraso do dia (regressão logística via Block+Trainer).
 *
 * Treino com curvas sintéticas determinísticas (heurísticas de ritmo;
 * capacidade menor em fins de semana). Com histórico real de adesão basta
 * trocar X/y — as assinaturas públicas não mudam.
 */
final class Modelos {
    private Modelos() {}

    static final float ESCALA_MINUTOS = 120f;
    private static final float LR = 0.05f;
    private static final int EPOCHS = 300;

    // ------------------------------------------------------------------
    // 1) Carga sustentável — TensorFlow (regressão linear, tensores puros)
    // ------------------------------------------------------------------
    /** Pesos [w1,w2,w3,b] treinados com tensores do TensorFlow (MSE + GD). */
    static float[] treinarCarga(NDManager manager) {
        float[][] X = new float[140][4];
        float[][] y = new float[140][1];
        Random rnd = new Random(7L);
        for (int i = 0; i < X.length; i++) {
            int diaSemana = i % 7; // 0=segunda ... 6=domingo
            float base = (diaSemana >= 5) ? 45f : 90f; // fim de semana: capacidade menor
            float minutos = Math.max(0f, base + (rnd.nextFloat() - 0.5f) * 40f);
            X[i][0] = minutos / ESCALA_MINUTOS;
            X[i][1] = (diaSemana >= 5 ? 1f : 3f) / 6f;
            X[i][2] = diaSemana / 6f;
            X[i][3] = 1f; // bias
            y[i][0] = minutos / ESCALA_MINUTOS;
        }
        NDArray xN = manager.create(X);
        NDArray yN = manager.create(y);
        NDArray w = manager.create(new float[][] {{0.5f}, {0.5f}, {0.5f}, {0f}});

        for (int ep = 0; ep < EPOCHS; ep++) {
            NDArray err = xN.matMul(w).sub(yN);           // yhat − y
            NDArray grad = xN.transpose().matMul(err)     // ∂(MSE)/∂w
                                   .div(X.length);
            w = w.sub(grad.mul(LR));
        }
        float[] pesos2d = w.reshape(4).toFloatArray();
        return new float[] {pesos2d[0], pesos2d[1], pesos2d[2], pesos2d[3]};
    }

    /** Carga sustentável prevista (minutos) pelo modelo TF para o dia. */
    static float previrCarga(NDManager manager, float[] pesos,
                             float cargaNorm, float sessoesNorm, float diaNorm) {
        NDArray linha = manager.create(new float[][] {{cargaNorm, sessoesNorm, diaNorm, 1f}});
        NDArray wN = manager.create(new float[][] {{pesos[0]}, {pesos[1]},
                {pesos[2]}, {pesos[3]}});
        float yhat = linha.matMul(wN).toFloatArray()[0];
        return yhat * ESCALA_MINUTOS;
    }

    // ------------------------------------------------------------------
    // 2) Risco de atraso — PyTorch (regressão logística via Block/Trainer)
    // ------------------------------------------------------------------
    static Model treinarRisco(NDManager manager) throws IOException {
        Model modelo = Model.newInstance("risco", "PyTorch");
        modelo.setBlock(Linear.builder().setUnits(1).build());

        float[][] X = new float[210][3];
        float[][] y = new float[210][1];
        Random rnd = new Random(42L);
        for (int i = 0; i < X.length; i++) {
            int diaSemana = i % 7;
            float capacidade = ((diaSemana >= 5) ? 45f : 90f) / ESCALA_MINUTOS;
            float carga = Math.max(0f, capacidade + (rnd.nextFloat() - 0.45f) * 1.2f);
            X[i][0] = carga;
            X[i][1] = capacidade;
            X[i][2] = diaSemana / 6f;
            y[i][0] = (carga > capacidade * 1.05f) ? 1f : 0f;
        }
        treinar(modelo, manager, X, y, Loss.sigmoidBinaryCrossEntropyLoss(), 60);
        return modelo;
    }

    private static void treinar(Model modelo, NDManager manager,
                                float[][] X, float[][] y,
                                Loss loss, int epochs) throws IOException {
        NDArray xN = manager.create(X);
        NDArray yN = manager.create(y);

        var trainer = modelo.newTrainer(new DefaultTrainingConfig(loss));
        trainer.initialize(new Shape(32, 3));

        var dataset = new ArrayDataset.Builder()
                .setData(xN)
                .optLabels(yN)
                .setSampling(32, true)
                .build();

        try {
            EasyTrain.fit(trainer, epochs, dataset, dataset);
        } catch (ai.djl.translate.TranslateException e) {
            throw new IllegalStateException(
                    "Falha no treino (" + loss.getClass().getSimpleName() + ")", e);
        }
        trainer.close();
    }

    /** features.length == 3 → saída prob. de atraso (0..1). */
    static float preverRisco(Model modelo, NDManager manager, float[] features) {
        NDArray entrada = manager.create(new float[][] {features});
        try (var preditor = modelo.newPredictor(new NoopTranslator())) {
            NDList saida = preditor.predict(new NDList(entrada));
            return saida.get(0).getFloat();
        } catch (ai.djl.translate.TranslateException e) {
            throw new IllegalStateException("Falha na predição", e);
        }
    }
}
