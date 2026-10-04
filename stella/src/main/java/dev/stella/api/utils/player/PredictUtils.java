package dev.stella.api.utils.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PredictUtils {
    
    private static class AdvancedKalmanFilter {
        private Vec3d position;
        private Vec3d velocity;
        private Vec3d acceleration;
        private double[][] P = new double[9][9];
        private double dt = 0.05;
        private double processNoiseQ = 0.15;
        private double measurementNoiseR = 0.3;
        private double predictionConfidence = 0.0;
        private long lastUpdateTime = System.currentTimeMillis();
        private List<Vec3d> positionHistory = new ArrayList<>(50);
        private List<Vec3d> velocityHistory = new ArrayList<>(50);
        
        public AdvancedKalmanFilter(Vec3d initialPos) {
            this.position = initialPos;
            this.velocity = Vec3d.ZERO;
            this.acceleration = Vec3d.ZERO;
            
            for (int i = 0; i < 9; i++) {
                Arrays.fill(P[i], 0.0);
                P[i][i] = (i < 3) ? 25.0 : (i < 6) ? 10.0 : 5.0;
            }
        }
        
        private double[][] getStateTransitionMatrix() {
            return new double[][] {
                {1, 0, 0, dt, 0, 0, 0.5*dt*dt, 0, 0},
                {0, 1, 0, 0, dt, 0, 0, 0.5*dt*dt, 0},
                {0, 0, 1, 0, 0, dt, 0, 0, 0.5*dt*dt},
                {0, 0, 0, 1, 0, 0, dt, 0, 0},
                {0, 0, 0, 0, 1, 0, 0, dt, 0},
                {0, 0, 0, 0, 0, 1, 0, 0, dt},
                {0, 0, 0, 0, 0, 0, 1, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 1, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 1}
            };
        }
        
        public void predict() {
            double[][] F = getStateTransitionMatrix();
            
            double[] state = {
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                acceleration.x, acceleration.y, acceleration.z
            };
            
            double[] predictedState = new double[9];
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    predictedState[i] += F[i][j] * state[j];
                }
            }
            
            position = new Vec3d(predictedState[0], predictedState[1], predictedState[2]);
            velocity = new Vec3d(predictedState[3], predictedState[4], predictedState[5]);
            acceleration = new Vec3d(predictedState[6], predictedState[7], predictedState[8]);
            
            double[][] FP = matrixMultiply(F, P);
            double[][] FPT = matrixTranspose(FP);
            double[][] FPFT = matrixMultiply(FP, FPT);
            
            double[][] Q = new double[9][9];
            for (int i = 0; i < 9; i++) {
                Q[i][i] = processNoiseQ * (i < 3 ? 1.0 : i < 6 ? 0.5 : 0.1);
            }
            
            P = matrixAdd(FPFT, Q);
        }
        
        public void update(Vec3d measurement, Vec3d rawVelocity) {
            positionHistory.add(measurement);
            velocityHistory.add(rawVelocity);
            
            if (positionHistory.size() > 50) {
                positionHistory.remove(0);
                velocityHistory.remove(0);
            }
            
            double[][] H = new double[3][9];
            H[0][0] = H[1][1] = H[2][2] = 1.0;
            
            double[][] HT = matrixTranspose(H);
            double[][] PH = matrixMultiply(P, HT);
            double[][] HP = matrixMultiply(H, P);
            double[][] HPH = matrixMultiply(HP, HT);
            
            double[][] R = new double[3][3];
            double measurementVariance = calculateMeasurementVariance();
            for (int i = 0; i < 3; i++) {
                R[i][i] = measurementNoiseR * measurementVariance;
            }
            
            double[][] HPH_R = matrixAdd(HPH, R);
            double[][] K = matrixMultiply(PH, matrixInverse3x3(HPH_R));
            
            double[] z = {measurement.x, measurement.y, measurement.z};
            double[] Hx = {position.x, position.y, position.z};
            double[] y = new double[3];
            for (int i = 0; i < 3; i++) y[i] = z[i] - Hx[i];
            
            double[] Ky = new double[9];
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 3; j++) {
                    Ky[i] += K[i][j] * y[j];
                }
            }
            
            double[] newState = {
                position.x + Ky[0], position.y + Ky[1], position.z + Ky[2],
                velocity.x + Ky[3], velocity.y + Ky[4], velocity.z + Ky[5],
                acceleration.x + Ky[6], acceleration.y + Ky[7], acceleration.z + Ky[8]
            };
            
            position = new Vec3d(newState[0], newState[1], newState[2]);
            velocity = new Vec3d(newState[3], newState[4], newState[5]);
            acceleration = new Vec3d(newState[6], newState[7], newState[8]);
            
            double[][] I = identityMatrix(9);
            double[][] KH = matrixMultiply(K, H);
            double[][] I_KH = matrixSubtract(I, KH);
            P = matrixMultiply(I_KH, P);
            
            predictionConfidence = calculateConfidence();
            lastUpdateTime = System.currentTimeMillis();
        }
        
        private double calculateMeasurementVariance() {
            if (positionHistory.size() < 3) return 1.0;
            
            double variance = 0.0;
            Vec3d mean = Vec3d.ZERO;
            for (Vec3d pos : positionHistory) {
                mean = mean.add(pos);
            }
            mean = mean.multiply(1.0 / positionHistory.size());
            
            for (Vec3d pos : positionHistory) {
                variance += pos.squaredDistanceTo(mean);
            }
            variance /= positionHistory.size();
            
            return Math.max(0.1, Math.min(5.0, variance));
        }
        
        private double calculateConfidence() {
            if (positionHistory.size() < 10) return 0.0;
            
            double consistency = calculateMovementConsistency();
            double stability = calculateVelocityStability();
            double recency = Math.min(1.0, (System.currentTimeMillis() - lastUpdateTime) / 1000.0);
            
            return consistency * 0.4 + stability * 0.4 + recency * 0.2;
        }
        
        private double calculateMovementConsistency() {
            if (positionHistory.size() < 2) return 0.0;
            
            double totalDistance = 0.0;
            for (int i = 1; i < positionHistory.size(); i++) {
                totalDistance += positionHistory.get(i).distanceTo(positionHistory.get(i-1));
            }
            double avgDistance = totalDistance / (positionHistory.size() - 1);
            
            double variance = 0.0;
            for (int i = 1; i < positionHistory.size(); i++) {
                double dist = positionHistory.get(i).distanceTo(positionHistory.get(i-1));
                variance += Math.pow(dist - avgDistance, 2);
            }
            variance /= (positionHistory.size() - 1);
            
            return Math.exp(-variance);
        }
        
        private double calculateVelocityStability() {
            if (velocityHistory.size() < 2) return 0.0;
            
            double avgSpeed = 0.0;
            for (Vec3d vel : velocityHistory) {
                avgSpeed += vel.length();
            }
            avgSpeed /= velocityHistory.size();
            
            double speedVariance = 0.0;
            for (Vec3d vel : velocityHistory) {
                speedVariance += Math.pow(vel.length() - avgSpeed, 2);
            }
            speedVariance /= velocityHistory.size();
            
            return Math.exp(-speedVariance * 10.0);
        }
        
        public Vec3d getPosition() { return position; }
        public Vec3d getVelocity() { return velocity; }
        public Vec3d getAcceleration() { return acceleration; }
        public double getConfidence() { return predictionConfidence; }
        
        public void setNoiseParameters(double processNoise, double measurementNoise) {
            this.processNoiseQ = processNoise;
            this.measurementNoiseR = measurementNoise;
        }
        
        public void adaptNoiseBasedOnMovement() {
            if (velocityHistory.size() > 10) {
                double avgSpeed = velocityHistory.stream()
                    .mapToDouble(Vec3d::length)
                    .average()
                    .orElse(0.0);
                
                processNoiseQ = Math.min(0.5, 0.05 + avgSpeed * 0.1);
                measurementNoiseR = Math.min(1.0, 0.1 + avgSpeed * 0.2);
            }
        }
    }
    
    private static final ConcurrentHashMap<Integer, AdvancedKalmanFilter> kalmanFilters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, PredictionProfile> predictionProfiles = new ConcurrentHashMap<>();
    
    private static boolean enableKalman = true;
    private static double kalmanProcessNoise = 0.15;
    private static double kalmanMeasurementNoise = 0.3;
    private static double gravity = -0.08;
    private static double airResistance = 0.98;
    private static double groundFriction = 0.6;
    
    public enum PredictionMode {
        LINEAR,
        KALMAN,
        ADAPTIVE
    }
    
    private static PredictionMode currentMode = PredictionMode.KALMAN;
    
    private static class PredictionProfile {
        double movementPatternScore = 0.0;
        double predictabilityScore = 0.0;
        double averageSpeed = 0.0;
        double directionChangeRate = 0.0;
        List<Vec3d> recentDirections = new ArrayList<>();
        long lastPatternUpdate = System.currentTimeMillis();
        
        public void update(Vec3d newPosition, Vec3d newVelocity) {
            recentDirections.add(newVelocity.normalize());
            if (recentDirections.size() > 20) {
                recentDirections.remove(0);
            }
            
            averageSpeed = averageSpeed * 0.9 + newVelocity.length() * 0.1;
            
            if (recentDirections.size() > 1) {
                Vec3d lastDir = recentDirections.get(recentDirections.size() - 2);
                Vec3d currentDir = recentDirections.get(recentDirections.size() - 1);
                double dot = Math.abs(lastDir.dotProduct(currentDir));
                directionChangeRate = directionChangeRate * 0.9 + (1.0 - dot) * 0.1;
            }
            
            predictabilityScore = calculatePredictability();
            movementPatternScore = detectMovementPattern();
            lastPatternUpdate = System.currentTimeMillis();
        }
        
        private double calculatePredictability() {
            if (recentDirections.size() < 5) return 0.0;
            
            double consistency = 0.0;
            for (int i = 1; i < recentDirections.size(); i++) {
                double dot = recentDirections.get(i).dotProduct(recentDirections.get(i-1));
                consistency += Math.max(0, dot);
            }
            consistency /= (recentDirections.size() - 1);
            
            return consistency * (1.0 - directionChangeRate);
        }
        
        private double detectMovementPattern() {
            if (recentDirections.size() < 10) return 0.0;
            
            double straightLineScore = 0.0;
            double circlingScore = 0.0;
            double zigzagScore = 0.0;
            
            Vec3d firstDir = recentDirections.get(0);
            Vec3d lastDir = recentDirections.get(recentDirections.size() - 1);
            
            double overallDot = firstDir.dotProduct(lastDir);
            straightLineScore = Math.max(0, overallDot);
            
            double angleSum = 0.0;
            for (int i = 1; i < recentDirections.size(); i++) {
                double angle = Math.acos(Math.min(1.0, Math.max(-1.0, 
                    recentDirections.get(i).dotProduct(recentDirections.get(i-1)))));
                angleSum += angle;
            }
            
            double avgAngle = angleSum / (recentDirections.size() - 1);
            circlingScore = Math.exp(-Math.abs(avgAngle - 0.5));
            
            double directionChanges = 0;
            for (int i = 2; i < recentDirections.size(); i++) {
                Vec3d cross = recentDirections.get(i-2).crossProduct(recentDirections.get(i-1));
                double crossDot = cross.dotProduct(recentDirections.get(i-1).crossProduct(recentDirections.get(i)));
                if (crossDot < 0) directionChanges++;
            }
            
            zigzagScore = directionChanges / (recentDirections.size() - 2);
            
            return Math.max(straightLineScore, Math.max(circlingScore, zigzagScore));
        }
        
        public PredictionMode suggestOptimalMode() {
            if (predictabilityScore > 0.8) return PredictionMode.KALMAN;
            if (averageSpeed > 5.0) return PredictionMode.ADAPTIVE;
            return PredictionMode.LINEAR;
        }
    }
    
    private static double[][] matrixMultiply(double[][] A, double[][] B) {
        int m = A.length, n = A[0].length, p = B[0].length;
        double[][] result = new double[m][p];
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) {
                double sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += A[i][k] * B[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }
    
    private static double[][] matrixTranspose(double[][] M) {
        int m = M.length, n = M[0].length;
        double[][] result = new double[n][m];
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[j][i] = M[i][j];
            }
        }
        return result;
    }
    
    private static double[][] matrixAdd(double[][] A, double[][] B) {
        int m = A.length, n = A[0].length;
        double[][] result = new double[m][n];
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = A[i][j] + B[i][j];
            }
        }
        return result;
    }
    
    private static double[][] matrixSubtract(double[][] A, double[][] B) {
        int m = A.length, n = A[0].length;
        double[][] result = new double[m][n];
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = A[i][j] - B[i][j];
            }
        }
        return result;
    }
    
    private static double[][] identityMatrix(int size) {
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) {
            result[i][i] = 1.0;
        }
        return result;
    }
    
    private static double[][] matrixInverse3x3(double[][] M) {
        double a = M[0][0], b = M[0][1], c = M[0][2];
        double d = M[1][0], e = M[1][1], f = M[1][2];
        double g = M[2][0], h = M[2][1], i = M[2][2];
        
        double det = a*(e*i - f*h) - b*(d*i - f*g) + c*(d*h - e*g);
        
        if (Math.abs(det) < 1e-10) {
            return identityMatrix(3);
        }
        
        double invDet = 1.0 / det;
        
        return new double[][] {
            {(e*i - f*h)*invDet, (c*h - b*i)*invDet, (b*f - c*e)*invDet},
            {(f*g - d*i)*invDet, (a*i - c*g)*invDet, (c*d - a*f)*invDet},
            {(d*h - e*g)*invDet, (g*b - a*h)*invDet, (a*e - b*d)*invDet}
        };
    }
    
    public static Vec3d predictPosition(Entity entity, int ticks) {
        return predictPosition(entity, ticks, currentMode);
    }
    
    public static Vec3d predictPosition(Entity entity, int ticks, PredictionMode mode) {
        if (!isValidEntity(entity)) return entity.getPos();
        
        switch (mode) {
            case LINEAR:
                return predictLinear(entity, ticks);
            case KALMAN:
                return predictKalman(entity, ticks);
            case ADAPTIVE:
                return predictAdaptive(entity, ticks);
            default:
                return predictLinear(entity, ticks);
        }
    }
    
    private static boolean isValidEntity(Entity entity) {
        return entity != null && entity.isAlive();
    }
    
    private static Vec3d predictLinear(Entity entity, int ticks) {
        Vec3d currentPos = entity.getPos();
        Vec3d velocity = entity.getVelocity();
        
        Vec3d predicted = currentPos;
        Vec3d currentVel = velocity;
        
        for (int i = 0; i < ticks; i++) {
            predicted = predicted.add(currentVel);
            currentVel = applyPhysics(currentVel, entity.isOnGround());
        }
        
        return predicted;
    }
    
    private static Vec3d predictKalman(Entity entity, int ticks) {
        if (!enableKalman) return predictLinear(entity, ticks);
        
        int entityId = entity.getId();
        AdvancedKalmanFilter filter = kalmanFilters.get(entityId);
        
        if (filter == null) {
            filter = new AdvancedKalmanFilter(entity.getPos());
            filter.setNoiseParameters(kalmanProcessNoise, kalmanMeasurementNoise);
            kalmanFilters.put(entityId, filter);
        }
        
        filter.predict();
        filter.update(entity.getPos(), entity.getVelocity());
        filter.adaptNoiseBasedOnMovement();
        
        updatePredictionProfile(entity, entity.getPos(), entity.getVelocity());
        
        Vec3d currentState = filter.getPosition();
        Vec3d velocity = filter.getVelocity();
        Vec3d acceleration = filter.getAcceleration();
        
        Vec3d predicted = currentState;
        Vec3d currentVel = velocity;
        
        double dt = 0.05;
        
        for (int i = 0; i < ticks; i++) {
            predicted = predicted.add(currentVel);
            currentVel = currentVel.add(acceleration.multiply(dt));
            currentVel = applyPhysics(currentVel, entity.isOnGround());
            
            acceleration = acceleration.multiply(0.95);
        }
        
        return predicted;
    }
    
    private static Vec3d predictAdaptive(Entity entity, int ticks) {
        PredictionProfile profile = predictionProfiles.get(entity.getId());
        if (profile == null) {
            profile = new PredictionProfile();
            predictionProfiles.put(entity.getId(), profile);
        }
        
        PredictionMode optimalMode = profile.suggestOptimalMode();
        return predictPosition(entity, ticks, optimalMode);
    }
    
    private static Vec3d applyPhysics(Vec3d velocity, boolean onGround) {
        Vec3d result = velocity.multiply(onGround ? groundFriction : airResistance);
        
        if (!onGround) {
            result = result.add(0, gravity, 0);
        }
        
        double maxHorizontal = 5.0;
        double horizontal = Math.sqrt(result.x * result.x + result.z * result.z);
        if (horizontal > maxHorizontal) {
            double scale = maxHorizontal / horizontal;
            result = new Vec3d(result.x * scale, result.y, result.z * scale);
        }
        
        double maxVertical = 3.0;
        if (Math.abs(result.y) > maxVertical) {
            result = new Vec3d(result.x, Math.signum(result.y) * maxVertical, result.z);
        }
        
        return result;
    }
    
    private static void updatePredictionProfile(Entity entity, Vec3d position, Vec3d velocity) {
        PredictionProfile profile = predictionProfiles.get(entity.getId());
        if (profile == null) {
            profile = new PredictionProfile();
            predictionProfiles.put(entity.getId(), profile);
        }
        profile.update(position, velocity);
        lastUpdateTimes.put(entity.getId(), System.currentTimeMillis());
    }
    
    public static Vec3d calculateLeadPosition(Vec3d shooterPos, Entity target, double projectileSpeed) {
        return calculateLeadPosition(shooterPos, target, projectileSpeed, currentMode);
    }
    
    public static Vec3d calculateLeadPosition(Vec3d shooterPos, Entity target, double projectileSpeed, PredictionMode mode) {
        if (!isValidEntity(target) || projectileSpeed <= 0) {
            return target.getPos();
        }
        
        Vec3d targetPos = target.getPos();
        double distance = targetPos.distanceTo(shooterPos);
        double timeToTarget = distance / projectileSpeed;
        int predictionTicks = Math.max(1, (int) Math.ceil(timeToTarget * 20));
        
        Vec3d predictedPos = predictPosition(target, predictionTicks, mode);
        
        double adjustment = calculateBallisticAdjustment(shooterPos, predictedPos, projectileSpeed);
        if (adjustment > 0) {
            predictedPos = predictedPos.add(0, adjustment, 0);
        }
        
        return predictedPos;
    }
    
    private static double calculateBallisticAdjustment(Vec3d shooterPos, Vec3d targetPos, double speed) {
        double horizontalDist = Math.sqrt(
            Math.pow(targetPos.x - shooterPos.x, 2) +
            Math.pow(targetPos.z - shooterPos.z, 2)
        );
        
        double verticalDist = targetPos.y - shooterPos.y;
        double time = horizontalDist / speed;
        
        double gravityDrop = 0.5 * 9.8 * time * time;
        double angleAdjustment = Math.atan(verticalDist / horizontalDist);
        
        return gravityDrop * Math.sin(angleAdjustment);
    }
    
    public static Vec3d predictPositionWithHistory(Entity entity, int ticks, int historyWeight) {
        List<Vec3d> predictions = new ArrayList<>();
        
        for (int i = 1; i <= Math.min(historyWeight, 10); i++) {
            int adjustedTicks = ticks + (i - historyWeight / 2);
            if (adjustedTicks > 0) {
                predictions.add(predictPosition(entity, adjustedTicks));
            }
        }
        
        return weightedAverage(predictions);
    }
    
    public static Vec3d predictPositionSeconds(Entity entity, double seconds) {
        int ticks = (int) (seconds * 20);
        return predictPosition(entity, ticks);
    }
    
    public static boolean willReachPosition(Entity entity, Vec3d targetPos, int maxTicks, double tolerance) {
        for (int i = 1; i <= maxTicks; i++) {
            Vec3d predicted = predictPosition(entity, i);
            if (predicted.distanceTo(targetPos) <= tolerance) {
                return true;
            }
        }
        return false;
    }
    
    public static Vec3d getPredictedHitPos(Entity entity, int ticks) {
        Vec3d predicted = predictPosition(entity, ticks);
        double heightOffset = entity.getHeight() / 2.0;
        return predicted.add(0, heightOffset, 0);
    }
    
    public static Vec3d getPredictedHitPos(Entity entity, int ticks, double heightMultiplier) {
        Vec3d predicted = predictPosition(entity, ticks);
        double heightOffset = entity.getHeight() * heightMultiplier;
        return predicted.add(0, heightOffset, 0);
    }
    
    public static Vec3d predictMovement(Entity entity, int ticks) {
        Vec3d current = entity.getPos();
        Vec3d predicted = predictPosition(entity, ticks);
        return predicted.subtract(current);
    }
    
    private static Vec3d interpolateVectors(Vec3d a, Vec3d b, double weight) {
        double invWeight = 1.0 - weight;
        return new Vec3d(
            a.x * weight + b.x * invWeight,
            a.y * weight + b.y * invWeight,
            a.z * weight + b.z * invWeight
        );
    }
    
    private static Vec3d weightedAverage(List<Vec3d> vectors) {
        if (vectors.isEmpty()) return Vec3d.ZERO;
        
        Vec3d sum = Vec3d.ZERO;
        for (Vec3d vec : vectors) {
            sum = sum.add(vec);
        }
        
        return sum.multiply(1.0 / vectors.size());
    }
    
    public static Vec3d getFilteredVelocity(Entity entity) {
        AdvancedKalmanFilter filter = kalmanFilters.get(entity.getId());
        return filter != null ? filter.getVelocity() : entity.getVelocity();
    }
    
    public static void setPredictionMode(PredictionMode mode) {
        currentMode = mode;
    }
    
    public static void setKalmanEnabled(boolean enabled) {
        enableKalman = enabled;
    }
    
    public static void setNoiseParameters(double processNoise, double measurementNoise) {
        kalmanProcessNoise = processNoise;
        kalmanMeasurementNoise = measurementNoise;
        
        for (AdvancedKalmanFilter filter : kalmanFilters.values()) {
            filter.setNoiseParameters(processNoise, measurementNoise);
        }
    }
    
    public static void setPhysicsParameters(double gravity, double airResistance, double groundFriction) {
        PredictUtils.gravity = gravity;
        PredictUtils.airResistance = airResistance;
        PredictUtils.groundFriction = groundFriction;
    }
    
    public static void cleanup() {
        long currentTime = System.currentTimeMillis();
        long timeout = 30000;
        
        kalmanFilters.keySet().removeIf(id -> {
            Long lastUpdate = lastUpdateTimes.get(id);
            return lastUpdate == null || (currentTime - lastUpdate) > timeout;
        });
        
        predictionProfiles.keySet().removeIf(id -> {
            Long lastUpdate = lastUpdateTimes.get(id);
            return lastUpdate == null || (currentTime - lastUpdate) > timeout;
        });
        
        lastUpdateTimes.keySet().removeIf(id -> {
            Long lastUpdate = lastUpdateTimes.get(id);
            return lastUpdate == null || (currentTime - lastUpdate) > timeout;
        });
    }
    
    public static void resetEntity(Entity entity) {
        if (entity != null) {
            int id = entity.getId();
            kalmanFilters.remove(id);
            predictionProfiles.remove(id);
            lastUpdateTimes.remove(id);
        }
    }
    
    public static void clearAll() {
        kalmanFilters.clear();
        predictionProfiles.clear();
        lastUpdateTimes.clear();
    }
    
    public static double getPredictionConfidence(Entity entity) {
        AdvancedKalmanFilter filter = kalmanFilters.get(entity.getId());
        return filter != null ? filter.getConfidence() : 0.0;
    }
    
    public static String getPredictionInfo(Entity entity) {
        PredictionProfile profile = predictionProfiles.get(entity.getId());
        if (profile == null) return "No profile";
        
        return String.format("Mode: %s, Conf: %.2f, Speed: %.1f, Predict: %.2f",
            currentMode.name(),
            getPredictionConfidence(entity),
            profile.averageSpeed,
            profile.predictabilityScore
        );
    }
    
    public static int getActiveFilterCount() {
        return kalmanFilters.size();
    }
    
    public static PredictionMode getCurrentMode() {
        return currentMode;
    }
}
