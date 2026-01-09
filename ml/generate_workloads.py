import pandas as pd
import numpy as np
import os
from datetime import datetime, timedelta

os.makedirs("../data", exist_ok=True)

def generate_cloud_data(profile, n_tasks=1000):
    start_time = datetime(2024, 1, 1, 0, 0)
    timestamps = [start_time + timedelta(minutes=i) for i in range(n_tasks)]

    if profile == "steady":
        cpu = np.random.normal(40, 5, n_tasks)
        mem = np.random.normal(4000, 200, n_tasks)
        users = np.random.normal(3000, 200, n_tasks)
    elif profile == "diurnal":
        cpu = 50 + 30 * np.sin(np.linspace(0, 6*np.pi, n_tasks)) + np.random.normal(0, 5, n_tasks)
        mem = 5000 + 800 * np.sin(np.linspace(0, 6*np.pi, n_tasks)) + np.random.normal(0, 300, n_tasks)
        users = 2000 + 1000 * np.sin(np.linspace(0, 4*np.pi, n_tasks)) + np.random.normal(0, 100, n_tasks)
    elif profile == "bursty":
        cpu = np.random.choice([30, 90], n_tasks, p=[0.8, 0.2]) + np.random.normal(0, 10, n_tasks)
        mem = np.random.choice([3000, 7000], n_tasks, p=[0.8, 0.2]) + np.random.normal(0, 500, n_tasks)
        users = np.random.choice([2000, 5000], n_tasks, p=[0.8, 0.2]) + np.random.normal(0, 300, n_tasks)
    else:
        raise ValueError("Unknown profile type")

    # 🔹 Smarter throughput pattern
    throughput = (cpu / 2.5) + (users / 2000) + np.random.normal(0, 1, n_tasks)
    throughput = np.clip(throughput, 2, 20)

    df = pd.DataFrame({
        "Task_Start_Time": timestamps,
        "CPU_Utilization (%)": np.clip(cpu, 0, 100),
        "Memory_Consumption (MB)": np.clip(mem, 1000, 8000),
        "Task_Execution_Time (ms)": np.random.randint(500, 3000, n_tasks),
        "System_Throughput (tasks/sec)": throughput,
        "Number_of_Active_Users": np.clip(users, 1000, 6000),
        "Network_Bandwidth_Utilization (Mbps)": np.random.uniform(50, 500, n_tasks),
        "Job_Priority": np.random.choice(["Low", "Medium", "High"], n_tasks, p=[0.4, 0.4, 0.2]),
        "Scheduler_Type": np.random.choice(["FCFS", "Priority-Based", "Round-Robin"], n_tasks),
        "Resource_Allocation_Type": np.random.choice(["Static", "Dynamic"], n_tasks)
    })

    return df
# Generate 3 workload profiles
for p in ["steady", "diurnal", "bursty"]:
    df = generate_cloud_data(p)
    df.to_csv(f"../data/cloud_workload_{p}.csv", index=False)
    print(f"✅ Generated cloud_workload_{p}.csv ({len(df)} records)")