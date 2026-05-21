package com.example.slam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取和解析JSONL文件中的IMU数据
 * 预加载所有数据到内存中，支持按索引访问
 */
@Service
public class JsonlImuService {

    private static final Logger log = LoggerFactory.getLogger(JsonlImuService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${slam.jsonl.path:calib_10m_1(1).jsonl}")
    private String jsonlPath;

    private final List<ImuDataEntry> allImuData = new ArrayList<>();

    public static class ImuDataEntry {
        public final long systemTime;
        public final double yaw;
        public final double pitch;
        public final double roll;
        public final double[] quat; // 四元数 [w, x, y, z]
        public final double[] acc;   // 加速度 [x, y, z]
        public final double[] gyr;   // 陀螺仪 [x, y, z]
        public final double[] mag;    // 磁力计 [x, y, z]

        public ImuDataEntry(long systemTime, double yaw, double pitch, double roll,
                           double[] quat, double[] acc, double[] gyr, double[] mag) {
            this.systemTime = systemTime;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.quat = quat;
            this.acc = acc;
            this.gyr = gyr;
            this.mag = mag;
        }
    }

    @PostConstruct
    public void init() {
        long startTime = System.currentTimeMillis();
        try {
            // 尝试多个可能的路径
            String[] candidates = {
                jsonlPath,
                "../" + jsonlPath,
                "../../" + jsonlPath,
                "backend/" + jsonlPath,
                new File(jsonlPath).getAbsolutePath(),
                new File("../" + jsonlPath).getAbsolutePath()
            };

            java.io.File resolvedFile = null;
            for (String candidate : candidates) {
                java.io.File f = new File(candidate);
                log.info("Trying path: {} -> {}", candidate, f.getAbsolutePath());
                if (f.exists()) {
                    resolvedFile = f;
                    log.info("Found JSONL file at: {}", f.getAbsolutePath());
                    break;
                }
            }

            if (resolvedFile == null) {
                log.error("JSONL file not found! Tried paths:");
                for (String candidate : candidates) {
                    log.error("  - {} (abs: {})", candidate, new File(candidate).getAbsolutePath());
                }
                log.error("Current working directory: {}", System.getProperty("user.dir"));
                return;
            }

            // 读取并解析JSONL文件
            List<String> lines = Files.readAllLines(resolvedFile.toPath());
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                try {
                    JsonNode node = objectMapper.readTree(line);

                    long systemTime = node.get("system_time").asLong();
                    double yaw = node.get("yaw").asDouble();
                    double pitch = node.get("pitch").asDouble();
                    double roll = node.get("roll").asDouble();

                    // 解析四元数
                    double[] quat = new double[4];
                    JsonNode quatNode = node.get("quat");
                    for (int i = 0; i < 4; i++) {
                        quat[i] = quatNode.get(i).asDouble();
                    }

                    // 解析加速度
                    double[] acc = new double[3];
                    JsonNode accNode = node.get("acc");
                    for (int i = 0; i < 3; i++) {
                        acc[i] = accNode.get(i).asDouble();
                    }

                    // 解析陀螺仪
                    double[] gyr = new double[3];
                    JsonNode gyrNode = node.get("gyr");
                    for (int i = 0; i < 3; i++) {
                        gyr[i] = gyrNode.get(i).asDouble();
                    }

                    // 解析磁力计
                    double[] mag = new double[3];
                    JsonNode magNode = node.get("mag");
                    for (int i = 0; i < 3; i++) {
                        mag[i] = magNode.get(i).asDouble();
                    }

                    allImuData.add(new ImuDataEntry(systemTime, yaw, pitch, roll,
                            quat, acc, gyr, mag));

                } catch (Exception e) {
                    log.warn("Failed to parse JSONL line: {}", e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Loaded {} IMU data entries in {}ms.", allImuData.size(), elapsed);

        } catch (Exception e) {
            log.error("Failed to load JSONL file: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取总数据条数
     */
    public int getTotalEntryCount() {
        return allImuData.size();
    }

    /**
     * 根据索引获取IMU数据（O(1)内存访问）
     */
    public ImuDataEntry getImuDataByIndex(int index) {
        if (index < 0 || index >= allImuData.size()) return null;
        return allImuData.get(index);
    }

    /**
     * 获取所有IMU数据
     */
    public List<ImuDataEntry> getAllImuData() {
        return allImuData;
    }

    /**
     * 获取第一条数据的时间戳
     */
    public long getMinTimestamp() {
        if (allImuData.isEmpty()) return 0;
        return allImuData.get(0).systemTime;
    }

    /**
     * 获取最后一条数据的时间戳
     */
    public long getMaxTimestamp() {
        if (allImuData.isEmpty()) return 0;
        return allImuData.get(allImuData.size() - 1).systemTime;
    }
}
