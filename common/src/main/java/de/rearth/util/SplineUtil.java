package de.rearth.util;

import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class SplineUtil {
    
    // t is in range 0-1
    public static Vec3d getPositionOnSpline(Vec3d start, Vec3d startDir, Vec3d end, Vec3d endDir, List<Pair<BlockPos, Direction>> middlePoints, double t) {
        
        t = Math.clamp(t, 0, 1);
        
        var transformedMidPoints = middlePoints.stream().map(elem -> new Pair<>(elem.getLeft().toCenterPos(), Vec3d.of(elem.getRight().getVector()))).toList();
        
        var allPairs = getPointPairs(start, startDir, end, endDir, transformedMidPoints);
        
        var segmentLengths = new Double[allPairs.size() - 1];
        var totalLength = 0d;
        for (int i = 0; i < allPairs.size() - 1; i++) {
            var from = allPairs.get(i);
            var to = allPairs.get(i + 1);
            var length = getLineLength(from.getLeft(), from.getRight(), to.getLeft(), to.getRight().multiply(1));
            segmentLengths[i] = (length);
            totalLength += length;
        }
        
        var targetLength = totalLength * t;
        var traversedLength = 0d;
        
        // traverse segments, if traversed dist matches segment, get the final point along it
        for (int i = 0; i < allPairs.size() - 1; i++) {
            var segmentLength = segmentLengths[i];
            
            if (targetLength >= traversedLength && targetLength < (traversedLength + segmentLength)) {
                var from = allPairs.get(i);
                var to = allPairs.get(i + 1);
                var offset = targetLength - traversedLength;
                var delta = offset / segmentLength;
                
                return getPointOnHermiteSpline(from.getLeft(), from.getRight().multiply(segmentLength * 1.5f), to.getLeft(), to.getRight().multiply(segmentLength * 1.5F), delta);
            } else {
                traversedLength += segmentLength;
            }
            
        }
        
        return end;
    }
    
    // approximates segment length by sampling 2 points along the line, and returning the total distance
    public static double getLineLength(Vec3d from, Vec3d fromTangent, Vec3d to, Vec3d toTangent) {
        
        if (true)
            return from.distanceTo(to);
        
        var approxLength = from.distanceTo(to);
        if (fromTangent.squaredDistanceTo(toTangent) < 0.1)
            approxLength += 1;
        
        var midPointA = getPointOnHermiteSpline(from, fromTangent.multiply(approxLength), to, toTangent.multiply(approxLength), 0.33f);
        var midPointB = getPointOnHermiteSpline(from, fromTangent.multiply(approxLength), to, toTangent.multiply(approxLength), 0.36f);
        
        
        return from.distanceTo(midPointA) + midPointA.distanceTo(midPointB) + midPointB.distanceTo(to);
    }
    
    public static double getTotalLength(List<Pair<Vec3d, Vec3d>> points) {
        
        var res = 0d;
        
        for (int i = 0; i < points.size() - 1; i++) {
            var current = points.get(i);
            var next = points.get(i + 1);
            var segmentLength = getLineLength(current.getLeft(), current.getRight(), next.getLeft(), next.getRight());
            res += segmentLength;
        }
        
        return res;
        
    }
    
    // calculates the facing of the middle points automatically. Returns a pair for each point with the desired tangent (to the next point)
    public static List<Pair<Vec3d, Vec3d>> getPointPairs(Vec3d start, Vec3d startDir, Vec3d end, Vec3d endDir, List<Pair<Vec3d, Vec3d>> middlePoints) {
        
        var pendingPoints = new ArrayList<Pair<Vec3d, Vec3d>>();
        pendingPoints.addAll(middlePoints);
        pendingPoints.add(new Pair<>(end, endDir));
        
        var pointsWithTangents = new ArrayList<Pair<Vec3d, Vec3d>>();
        pointsWithTangents.add(new Pair<>(start, startDir));
        
        var currentFrom = start.add(startDir.multiply(0.3f));
        
        while (!pendingPoints.isEmpty()) {
            var pair = pendingPoints.removeFirst();
            
            if (pair.getLeft().equals(end)) {
                pointsWithTangents.add(new Pair<>(end, endDir));
                break;
            }
            
            var currentTo = pair.getLeft();
            var distA = currentFrom.distanceTo(pair.getLeft().add(pair.getRight()));
            var distB = currentFrom.distanceTo(pair.getLeft().subtract(pair.getRight()));
            var currentToDir = distA > distB ? pair.getRight() : pair.getRight().multiply(-1);
            
            currentFrom = currentTo.add(currentToDir.multiply(-0.3f));
            
            pointsWithTangents.add(new Pair<>(currentTo, currentToDir));
        }
        
        return pointsWithTangents;
        
    }
    
    /**
     * Calculates a point on a cubic Hermite spline.
     *
     * @param pointA   The starting point of the spline (P0).
     * @param tangentA The tangent vector (derivative) at pointA (M0). The curve will start
     *                 moving in this direction with a "velocity" given by its magnitude.
     * @param pointB   The ending point of the spline (P1).
     * @param tangentB The tangent vector (derivative) at pointB (M1). The curve will arrive
     *                 at pointB with this tangent.
     * @param t        The interpolation parameter, ranging from 0.0 (returns pointA) to 1.0 (returns pointB).
     *                 Values outside this range will be clamped.
     * @return A Vec3d representing the point on the Hermite spline at parameter t.
     */
    public static Vec3d getPointOnHermiteSpline(Vec3d pointA, Vec3d tangentA, Vec3d pointB, Vec3d tangentB, double t) {
        // Clamp t to the range [0, 1]
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;
        
        double t2 = t * t;
        double t3 = t2 * t;
        
        // Hermite basis functions
        double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
        double h10 = t3 - 2.0 * t2 + t;
        double h01 = -2.0 * t3 + 3.0 * t2;
        double h11 = t3 - t2;
        
        // Calculate the point on the spline
        // H(t) = h00(t)*P0 + h10(t)*M0 + h01(t)*P1 + h11(t)*M1
        Vec3d termP0 = pointA.multiply(h00);
        Vec3d termM0 = tangentA.multiply(h10);
        Vec3d termP1 = pointB.multiply(h01);
        Vec3d termM1 = tangentB.multiply(h11);
        
        return termP0.add(termM0).add(termP1).add(termM1);
    }
    
}
