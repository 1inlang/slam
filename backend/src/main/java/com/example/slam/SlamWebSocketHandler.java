package com.example.slam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SlamWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SlamWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JsonlImuService jsonlImuService;

    // 当前数据索引
    private int currentIndex = 0;
    private boolean isPlaying = true;

    // 模拟位置（基于时间和简单积分）
    private double posX = 0.0;
    private double posY = 0.0;
    private double posZ = 0.0;
    private double lastTimestamp = -1;

    public SlamWebSocketHandler() {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
        // 发送第一条数据
        sendImuData(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket connection closed: {}", session.getId());
    }

    @Scheduled(fixedRate = 100) // 每100ms发送一次数据
    public void sendData() {
        if (sessions.isEmpty() || !isPlaying) return;

        // 广播给所有连接的客户端
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    sendImuData(session);
                } catch (IOException e) {
                    log.error("Failed to send data to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }

        // 推进到下一帧
        currentIndex++;
        int totalEntries = jsonlImuService.getTotalEntryCount();
        if (totalEntries > 0 && currentIndex >= totalEntries) {
            currentIndex = 0; // 循环播放
            resetPosition();
            log.info("Reached end of data, looping back to start");
        }
    }

    private void sendImuData(WebSocketSession session) throws IOException {
        int totalEntries = jsonlImuService.getTotalEntryCount();
        if (totalEntries == 0) {
            log.warn("No IMU data available");
            return;
        }

        // 确保索引在有效范围内
        if (currentIndex >= totalEntries) {
            currentIndex = 0;
        }

        JsonlImuService.ImuDataEntry entry = jsonlImuService.getImuDataByIndex(currentIndex);
        if (entry == null) return;

        // 计算位移（简化：基于加速度积分）
        updatePosition(entry);

        // 1. 发送Pose消息（包含位置和朝向）
        Map<String, Object> pose = new HashMap<>();
        pose.put("type", "pose");
        pose.put("timestamp", entry.systemTime);
        pose.put("x", posX);
        pose.put("y", posY);
        pose.put("height", posZ);
        pose.put("yaw", entry.yaw);
        pose.put("pitch", entry.pitch);
        pose.put("roll", entry.roll);

        // 添加四元数
        Map<String, double[]> quatMap = new HashMap<>();
        quatMap.put("quat", entry.quat);
        pose.putAll(quatMap);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pose)));

        // 2. 发送IMU原始数据消息
        Map<String, Object> imu = new HashMap<>();
        imu.put("type", "imu");
        imu.put("timestamp", entry.systemTime);
        imu.put("acc", entry.acc);
        imu.put("gyr", entry.gyr);
        imu.put("mag", entry.mag);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(imu)));

        if (currentIndex % 10 == 0) {
            log.debug("Sent frame {}/{}: timestamp={}", currentIndex, totalEntries, entry.systemTime);
        }
    }

    /**
     * 基于加速度数据更新位置（简化积分）
     * 注意：真实场景需要复杂的传感器融合算法
     */
    private void updatePosition(JsonlImuService.ImuDataEntry entry) {
        if (lastTimestamp < 0) {
            lastTimestamp = entry.systemTime;
            return;
        }

        // 计算时间差（毫秒转秒）
        double dt = (entry.systemTime - lastTimestamp) / 1000.0;
        if (dt <= 0) dt = 0.1; // 默认100ms

        // 简化：假设沿着yaw方向前进
        // 真实场景需要基于加速度二次积分 + 磁力计/陀螺仪融合
        double speed = 0.5; // 假设速度 0.5 m/s
        double yawRad = Math.toRadians(entry.yaw);

        // 更新位置（基于yaw方向前进）
        posX += speed * dt * Math.sin(yawRad);
        posY += speed * dt * Math.cos(yawRad);

        // 高度保持不变（或基于气压计数据）
        posZ = 1.2;

        lastTimestamp = entry.systemTime;
    }

    private void resetPosition() {
        posX = 0.0;
        posY = 0.0;
        posZ = 1.2;
        lastTimestamp = -1;
    }

    /**
     * 控制播放/暂停
     */
    public void togglePlayPause() {
        isPlaying = !isPlaying;
        log.info("Playback {}", isPlaying ? "resumed" : "paused");
    }

    /**
     * 跳转到指定索引
     */
    public void seekTo(int index) {
        int totalEntries = jsonlImuService.getTotalEntryCount();
        if (totalEntries == 0) return;

        currentIndex = Math.max(0, Math.min(index, totalEntries - 1));
        log.info("Seek to frame {}/{}", currentIndex, totalEntries);
    }

    /**
     * 获取当前状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentIndex", currentIndex);
        status.put("totalEntries", jsonlImuService.getTotalEntryCount());
        status.put("isPlaying", isPlaying);
        return status;
    }
}
