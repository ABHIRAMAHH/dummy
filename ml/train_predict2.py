
import os
import pickle
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from sklearn.preprocessing import MinMaxScaler, LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

import tensorflow as tf
from tensorflow.keras import Model, Input
from tensorflow.keras.layers import Conv1D, GRU, Dense, Dropout
from tensorflow.keras.losses import Huber
from tensorflow.keras.callbacks import EarlyStopping


# =========================
# Config
# =========================
DATA_PATH = "../data/cloud_workload_steady.csv"
MODEL_DIR = "models"
RESULTS_DIR = "../results"

TIME_COL = "Task_Start_Time"
TARGET_COL = "System_Throughput (tasks/sec)"

SEQ_LEN = 10              # smaller window (as you intended)
TRAIN_RATIO = 0.80
DEFAULT_THROUGHPUT = 5.0

FEATURE_COLS = [
    "Job_Priority",
    "CPU_Utilization (%)",
    "Memory_Consumption (MB)",
    "Hour", "Day", "Month",
    "Prev_Throughput_1",
    "Prev_Throughput_2",
    "Prev_Throughput_3",
]

RANDOM_SEED = 42


# =========================
# Helpers
# =========================
def ensure_dirs():
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(RESULTS_DIR, exist_ok=True)

def build_sequences(X_2d, y_2d, seq_len: int):
    X_seq, y_seq, idx = [], [], []
    for i in range(seq_len, len(X_2d)):
        X_seq.append(X_2d[i-seq_len:i])
        y_seq.append(y_2d[i])
        idx.append(i)
    return np.array(X_seq), np.array(y_seq), np.array(idx)

def split_time_series(X_seq, y_seq, idx, train_ratio: float):
    split = int(train_ratio * len(X_seq))
    return (X_seq[:split], X_seq[split:],
            y_seq[:split], y_seq[split:],
            idx[:split], idx[split:])

def fit_transform_scalers_no_leakage(X_train_raw, X_test_raw, y_train_raw, y_test_raw):
    x_scaler = MinMaxScaler()
    y_scaler = MinMaxScaler()

    X_train_2d = X_train_raw.reshape(-1, X_train_raw.shape[-1])
    x_scaler.fit(X_train_2d)
    y_scaler.fit(y_train_raw)

    X_train = x_scaler.transform(X_train_raw.reshape(-1, X_train_raw.shape[-1])).reshape(X_train_raw.shape)
    X_test = x_scaler.transform(X_test_raw.reshape(-1, X_test_raw.shape[-1])).reshape(X_test_raw.shape)
    y_train = y_scaler.transform(y_train_raw)
    y_test = y_scaler.transform(y_test_raw)

    return X_train, X_test, y_train, y_test, x_scaler, y_scaler

def export_aligned_predictions(df_raw, pred_dfraw_idx, y_pred_inv, out_path, default_value=5.0):
    full_pred = np.full((len(df_raw),), float(default_value), dtype=float)
    full_pred[pred_dfraw_idx] = y_pred_inv.reshape(-1)

    df_out = df_raw.copy()
    df_out["Predicted_Throughput (tasks/sec)"] = full_pred
    df_out.to_csv(out_path, index=False)
    return df_out

def compute_metrics(y_true, y_pred):
    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2 = r2_score(y_true, y_pred)
    return mae, rmse, r2


# =========================
# Main
# =========================
def main():
    tf.random.set_seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)
    ensure_dirs()

    # 1) Load raw dataset
    df_raw = pd.read_csv(DATA_PATH)
    df_raw[TIME_COL] = pd.to_datetime(df_raw[TIME_COL])

    # 2) Feature engineering
    df = df_raw.copy()
    df["Hour"] = df[TIME_COL].dt.hour
    df["Day"] = df[TIME_COL].dt.day
    df["Month"] = df[TIME_COL].dt.month

    df["Prev_Throughput_1"] = df[TARGET_COL].shift(1)
    df["Prev_Throughput_2"] = df[TARGET_COL].shift(2)
    df["Prev_Throughput_3"] = df[TARGET_COL].shift(3)

    df = df.dropna().reset_index(drop=False)  # keep original row index
    orig_idx = df["index"].to_numpy(dtype=int)

    # Encode Job_Priority
    if df["Job_Priority"].dtype == "object":
        le = LabelEncoder()
        df["Job_Priority"] = le.fit_transform(df["Job_Priority"].astype(str))
        with open(os.path.join(MODEL_DIR, "job_priority_encoder.pkl"), "wb") as f:
            pickle.dump(le, f)

    # 3) Select features/target
    X_raw = df[FEATURE_COLS].to_numpy(dtype=np.float32)
    y_raw = df[[TARGET_COL]].to_numpy(dtype=np.float32)

    # 4) Build sequences (raw/unscaled)
    X_seq_raw, y_seq_raw, seq_idx_in_df = build_sequences(X_raw, y_raw, SEQ_LEN)
    pred_dfraw_idx = orig_idx[seq_idx_in_df]

    # 5) Time split
    X_train_raw, X_test_raw, y_train_raw, y_test_raw, idx_train, idx_test = split_time_series(
        X_seq_raw, y_seq_raw, pred_dfraw_idx, TRAIN_RATIO
    )

    # 6) Scale (no leakage)
    X_train, X_test, y_train, y_test, x_scaler, y_scaler = fit_transform_scalers_no_leakage(
        X_train_raw, X_test_raw, y_train_raw, y_test_raw
    )

    with open(os.path.join(MODEL_DIR, "x_scaler.pkl"), "wb") as f:
        pickle.dump(x_scaler, f)
    with open(os.path.join(MODEL_DIR, "y_scaler.pkl"), "wb") as f:
        pickle.dump(y_scaler, f)

    # 7) Build Functional CNN + GRU model
    inp = Input(shape=(X_train.shape[1], X_train.shape[2]), name="seq_input")

    x = Conv1D(filters=64, kernel_size=3, padding="same", activation="relu")(inp)
    x = Dropout(0.3)(x)

    x = GRU(128, return_sequences=True, activation="tanh")(x)
    x = Dropout(0.3)(x)
    x = GRU(64, activation="tanh")(x)
    x = Dropout(0.3)(x)

    x = Dense(32, activation="relu")(x)
    out = Dense(1, name="throughput")(x)

    model = Model(inputs=inp, outputs=out, name="cnn_gru_throughput_predictor")
    model.compile(optimizer="adam", loss=Huber())

    early_stop = EarlyStopping(monitor="val_loss", patience=15, restore_best_weights=True)

    history = model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=120,
        batch_size=32,
        callbacks=[early_stop],
        verbose=1
    )

    model.save(os.path.join(MODEL_DIR, "cnn_gru_predictor.keras"))

    # 8) Evaluate on test
    y_pred_test = model.predict(X_test, verbose=0)
    y_pred_test_inv = y_scaler.inverse_transform(y_pred_test)
    y_test_inv = y_scaler.inverse_transform(y_test)

    mae, rmse, r2 = compute_metrics(y_test_inv, y_pred_test_inv)
    print(f"📊 MAE:  {mae:.3f}")
    print(f"📊 RMSE: {rmse:.3f}")
    print(f"📈 R²:   {r2:.3f}")

    # 9) Full prediction + aligned export
    X_seq_scaled = x_scaler.transform(X_seq_raw.reshape(-1, X_seq_raw.shape[-1])).reshape(X_seq_raw.shape)
    y_pred_full = model.predict(X_seq_scaled, verbose=0)
    y_pred_full_inv = y_scaler.inverse_transform(y_pred_full)

    out_csv = os.path.join(RESULTS_DIR, "cloud_workload_with_steady_predictions_cnn_gru.csv")
    export_aligned_predictions(
        df_raw=df_raw,
        pred_dfraw_idx=pred_dfraw_idx,
        y_pred_inv=y_pred_full_inv,
        out_path=out_csv,
        default_value=DEFAULT_THROUGHPUT
    )
    print(f"✅ Saved aligned predictions to: {out_csv}")

    # 10) Plot test segment
    plt.figure(figsize=(10, 5))
    plt.plot(y_test_inv, label="Actual", color="blue")
    plt.plot(y_pred_test_inv, label="Predicted (CNN+GRU)", color="red", linestyle="--")
    plt.xlabel("Test time steps")
    plt.ylabel("Throughput (tasks/sec)")
    plt.title("CNN+GRU: Actual vs Predicted Throughput")
    plt.legend()
    plt.tight_layout()

    out_png = os.path.join(RESULTS_DIR, "throughput_steady_prediction_cnn_gru.png")
    plt.savefig(out_png, dpi=150)
    plt.show()
    print(f"✅ Saved plot to: {out_png}")


if __name__ == "__main__":
    main()