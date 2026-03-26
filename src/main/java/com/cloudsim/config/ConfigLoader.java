//package com.cloudsim.config;
//
//import com.cloudsim.VmTemplate;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ConfigLoader {
//    public static List<VmTemplate> loadVmTemplates(String path) throws Exception {
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode root = mapper.readTree(new File(path));
//
//        List<VmTemplate> templates = new ArrayList<>();
//
//        // New dataset: JSON is a simple array (no "vm_types" key)
//        if (root.isArray()) {
//            for (JsonNode n : root) {
//                VmTemplate vt = new VmTemplate();
//                vt.name = n.has("name") ? n.get("name").asText() : "default";
//                vt.mips = n.has("mips") ? n.get("mips").asDouble() : 1000;
//                vt.ram = n.has("ram") ? n.get("ram").asInt() : 2048;
//                vt.pes = n.has("pes")  ? n.get("pes").asInt() :1;
//                vt.bw = n.has("bw") ? n.get("bw").asLong() : 1000;
//                vt.costPerSec = n.has("costPerSec") ? n.get("costPerSec").asDouble() : 0.01;
//                templates.add(vt);
//            }
//        } else {
//            System.err.println("⚠️ Expected an array in " + path + " but found: " + root.getNodeType());
//        }
//
//        return templates;
//    }
//}
package com.cloudsim.config;

import com.cloudsim.VmTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigLoader {

    public static List<VmTemplate> loadVmTemplates(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(path));

        // root may be an object { "vm_types": [...] }
        JsonNode arr = root.isArray() ? root : root.get("vm_types");
        if (arr == null || !arr.isArray()) {
            throw new IllegalArgumentException("Expected array or {vm_types:[...]} in " + path);
        }

        List<VmTemplate> templates = new ArrayList<>();
        for (JsonNode n : arr) {
            VmTemplate vt = new VmTemplate();
            vt.name = n.has("type") ? n.get("type").asText() : (n.has("name") ? n.get("name").asText() : "default");
            vt.mips = n.has("mips") ? n.get("mips").asDouble() : 1000;
            vt.ram  = n.has("ram") ? n.get("ram").asInt() : 1024;
            vt.bw   = n.has("bw")  ? n.get("bw").asLong() : 1000;
            vt.pes  = n.has("pes") ? n.get("pes").asInt() : 1;

            // Your JSON uses costPerSec
            vt.costPerSec = n.has("costPerSec") ? n.get("costPerSec").asDouble() : 0.01;

            templates.add(vt);
        }
        return templates;
    }
}