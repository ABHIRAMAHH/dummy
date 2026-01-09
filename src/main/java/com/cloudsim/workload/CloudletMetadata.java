//package com.cloudsim.workload;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class CloudletMetadata {
//    private static final Map<Long, WorkloadLoader.WorkloadTask> metadata = new HashMap<>();
//
//    public static void storeMetadata(long cloudletId, WorkloadLoader.WorkloadTask task) {
//        metadata.put(cloudletId, task);
//    }
//
//    public static WorkloadLoader.WorkloadTask getMetadata(long cloudletId) {
//        return metadata.get(cloudletId);
//    }
//
//    public static Map<Long, WorkloadLoader.WorkloadTask> getAll() {
//        return metadata;
//    }
//}
package com.cloudsim.workload;

import java.util.HashMap;
import java.util.Map;

public class CloudletMetadata {
    private static final Map<Long, WorkloadLoader.WorkloadTask> metadata = new HashMap<>();

    public static void storeMetadata(long cloudletId, WorkloadLoader.WorkloadTask task) {
        metadata.put(cloudletId, task);
    }

    public static WorkloadLoader.WorkloadTask getMetadata(long cloudletId) {
        return metadata.get(cloudletId);
    }

    public static Map<Long, WorkloadLoader.WorkloadTask> getAll() {
        return metadata;
    }
}
