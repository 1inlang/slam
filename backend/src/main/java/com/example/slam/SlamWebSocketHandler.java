package com.example.slam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Random;
@Component
public class SlamWebSocketHandler extends AbstractWebSocketHandler {
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Float> allPoints = new ArrayList<>();
    private final Random random = new Random();
    // Mock trajectory
    private float firefighterX = 0;
    private float firefighterY = 1.2f; // Height
    private float firefighterZ = 0;
    private float firefighterYaw = 0; 
    private float firefighterPitch = 0;
    private int firefighterFloor = 1;
    private long tickCount = 0;
    public SlamWebSocketHandler() {
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        sendFullMap(session);
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }
    private void sendFullMap(WebSocketSession session) {
        if (!session.isOpen()) return;
        try {
            int numFloats = allPoints.size();
            ByteBuffer buffer = ByteBuffer.allocate(1 + numFloats * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte) 0x01); 
            for (Float f : allPoints) {
                buffer.putFloat(f);
            }
            buffer.flip();
            session.sendMessage(new BinaryMessage(buffer));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Scheduled(fixedRate = 100) 
    public void sendData() {
        if (sessions.isEmpty()) return;
        tickCount++;
        float speed = 0.05f; 
        if (tickCount < 200) {
            firefighterZ += speed;
            firefighterPitch = 0;
        } else if (tickCount < 300) {
            firefighterYaw += 0.9f; 
            firefighterX += speed * (float)Math.sin(Math.toRadians(firefighterYaw));
            firefighterZ += speed * (float)Math.cos(Math.toRadians(firefighterYaw));
            firefighterPitch = 0;
        } else if (tickCount < 500) {
            firefighterX += speed * (float)Math.sin(Math.toRadians(firefighterYaw));
            firefighterZ += speed * (float)Math.cos(Math.toRadians(firefighterYaw));
            firefighterY += speed * 0.3f; 
            firefighterPitch = -20; 
            firefighterFloor = 2; 
        } else if (tickCount < 700) {
            firefighterX += speed * (float)Math.sin(Math.toRadians(firefighterYaw));
            firefighterZ += speed * (float)Math.cos(Math.toRadians(firefighterYaw));
            firefighterPitch = 0;
            firefighterFloor = 2;
        } else if (tickCount < 900) {
            firefighterYaw += 1.8f; 
            firefighterX += speed * (float)Math.sin(Math.toRadians(firefighterYaw));
            firefighterZ += speed * (float)Math.cos(Math.toRadians(firefighterYaw));
            firefighterY -= speed * 0.3f; 
            firefighterPitch = 20; 
            firefighterFloor = 1;
        } else {
             tickCount = 0;
             firefighterX = 0;
             firefighterY = 1.2f;
             firefighterZ = 0;
             firefighterYaw = 0;
             firefighterPitch = 0;
             firefighterFloor = 1;
             allPoints.clear();
             sendFullMapToAll(); 
             return;
        }
        Map<String, Object> pose = new HashMap<>();
        pose.put("type", "pose");
        pose.put("x", firefighterX);
        pose.put("y", firefighterZ); // Z is horizontal offset, mapped to Y
        pose.put("height", firefighterY);
        pose.put("timestamp", System.currentTimeMillis());
        pose.put("yaw", firefighterYaw);
        pose.put("pitch", firefighterPitch);
        pose.put("floor", firefighterFloor);
        List<Float> newPoints = new ArrayList<>();
        float leftWallX = firefighterX - 2.0f * (float)Math.cos(Math.toRadians(firefighterYaw)) + (random.nextFloat()-0.5f)*0.2f;
        float leftWallZ = firefighterZ + 2.0f * (float)Math.sin(Math.toRadians(firefighterYaw)) + (random.nextFloat()-0.5f)*0.2f;
        float wallY = firefighterY - 1.2f + random.nextFloat() * 3.0f; 
        newPoints.add(leftWallX); newPoints.add(wallY); newPoints.add(leftWallZ);
        float rightWallX = firefighterX + 2.0f * (float)Math.cos(Math.toRadians(firefighterYaw)) + (random.nextFloat()-0.5f)*0.2f;
        float rightWallZ = firefighterZ - 2.0f * (float)Math.sin(Math.toRadians(firefighterYaw)) + (random.nextFloat()-0.5f)*0.2f;
        float wallY2 = firefighterY - 1.2f + random.nextFloat() * 3.0f;
        newPoints.add(rightWallX); newPoints.add(wallY2); newPoints.add(rightWallZ);
        if (random.nextFloat() > 0.7) {
            float debrisX = firefighterX + (random.nextFloat()-0.5f)*3f;
            float debrisZ = firefighterZ + (random.nextFloat()-0.5f)*3f;
            float debrisY = firefighterY - 1.2f + random.nextFloat() * 0.5f; 
            newPoints.add(debrisX); newPoints.add(debrisY); newPoints.add(debrisZ);
        }
        for (Float f : newPoints) {
            allPoints.add(f);
        }
        ByteBuffer buffer = ByteBuffer.allocate(1 + newPoints.size() * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x02); 
        for (Float f : newPoints) {
            buffer.putFloat(f);
        }
        buffer.flip();
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pose)));
                    session.sendMessage(new BinaryMessage(buffer));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void sendFullMapToAll() {
        for (WebSocketSession session : sessions) {
            sendFullMap(session);
        }
    }
}
