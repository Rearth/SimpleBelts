package de.rearth.util;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

public class Spline {
    
    private static final double EPSILON = 1e-6; // For floating point comparisons
    
    /**
     * Calculates a point on a parameterized Catmull-Rom spline using Minecraft's Vec3d.
     * The spline passes through startPoint, all midPoints, and endPoint.
     * Tangents at start and end are controlled by startTangent and endTangent.
     *
     * @param progress       Progress along the entire spline (0.0 to 1.0).
     * @param startPoint     The starting point of the spline.
     * @param startTangent   The tangent vector (direction * strength) at the startPoint.
     * @param endPoint       The ending point of the spline.
     * @param endTangent     The tangent vector (direction * strength) at the endPoint.
     * @param alpha          Parameterization type: 0.0 for Uniform, 0.5 for Centripetal, 1.0 for Chordal.
     * @param tangentScale   Factor to scale the provided start/end tangents. Standard is 2.0.
     *                       Your original code used a factor equivalent to 10.0 here for phantom point calculation.
     * @param midPoints      Optional array of points the spline must pass through between start and end.
     * @return The Vec3d point on the spline corresponding to the given progress.
     */
    public static Vec3d getPointOnCatmullRomSpline(float progress,
                                                   Vec3d startPoint, Vec3d startTangent,
                                                   Vec3d endPoint, Vec3d endTangent,
                                                   double alpha, double tangentScale,
                                                   Vec3d... midPoints) {
        
        // 1. Clamp progress
        double t_global = Math.max(0.0, Math.min(1.0, progress));
        
        // 2. Collect all actual points the spline must pass through
        List<Vec3d> actualPointsList = new ArrayList<>();
        actualPointsList.add(startPoint);
        if (midPoints != null) {
            for (Vec3d midPoint : midPoints) {
                if (midPoint != null) { // Guard against nulls in midPoints array
                    actualPointsList.add(midPoint);
                }
            }
        }
        actualPointsList.add(endPoint);
        
        Vec3d[] actualPoints = actualPointsList.toArray(new Vec3d[0]);
        int numActualPoints = actualPoints.length;
        
        // Handle trivial cases
        if (numActualPoints == 0) {
            return new Vec3d(0,0,0); // Or throw exception
        }
        if (numActualPoints == 1) {
            return actualPoints[0];
        }
        
        // 3. Construct the full array of control points for Catmull-Rom.
        // P_minus_1, P_0, P_1, ..., P_N, P_N_plus_1
        // Where P_0 to P_N are the actual points.
        Vec3d[] catmullRomCPs = new Vec3d[numActualPoints + 2];
        
        // Copy actual points into the middle of catmullRomCPs
        for (int i = 0; i < numActualPoints; i++) {
            catmullRomCPs[i + 1] = actualPoints[i];
        }
        
        // Define P_minus_1 (phantom point before start to control start tangent)
        // Standard Catmull-Rom tangent at P_0_actual (catmullRomCPs[1]) is 0.5 * (P_1_actual - P_minus_1).
        // If we want this tangent to be startTangent:
        // startTangent = 0.5 * (catmullRomCPs[2] - P_minus_1)
        // 2 * startTangent = catmullRomCPs[2] - P_minus_1
        // P_minus_1 = catmullRomCPs[2] - 2 * startTangent
        // catmullRomCPs[2] is the point after startPoint (or endPoint if only start/end)
        Vec3d pointAfterStart = catmullRomCPs[2]; // actualPoints[1]
        catmullRomCPs[0] = pointAfterStart.subtract(startTangent.multiply(tangentScale));
        
        
        // Define P_N_plus_1 (phantom point after end to control end tangent)
        // Tangent at P_N_actual (catmullRomCPs[numActualPoints]) is 0.5 * (P_N_plus_1 - P_N-1_actual).
        // endTangent = 0.5 * (P_N_plus_1 - catmullRomCPs[numActualPoints - 1])
        // 2 * endTangent = P_N_plus_1 - catmullRomCPs[numActualPoints - 1]
        // P_N_plus_1 = catmullRomCPs[numActualPoints - 1] + 2 * endTangent
        // catmullRomCPs[numActualPoints - 1] is the point before endPoint (or startPoint if only start/end)
        Vec3d pointBeforeEnd = catmullRomCPs[numActualPoints - 1]; // actualPoints[numActualPoints - 2]
        catmullRomCPs[numActualPoints + 1] = pointBeforeEnd.add(endTangent.multiply(tangentScale));
        
        
        // 4. Determine segment parameter spans based on chord lengths and alpha
        int numSegments = numActualPoints - 1;
        if (numSegments <= 0) { // Should be caught by numActualPoints == 1 earlier
            return actualPoints[0];
        }
        
        double[] segmentParamSpans = new double[numSegments];
        double totalParamSpan = 0;
        
        for (int i = 0; i < numSegments; i++) {
            Vec3d p_i = actualPoints[i];       // This is catmullRomCPs[i+1]
            Vec3d p_i_plus_1 = actualPoints[i+1]; // This is catmullRomCPs[i+2]
            double distance = p_i.distanceTo(p_i_plus_1);
            
            // Handle alpha = 0 case for pow(0,0) which is 1.
            // If distance is 0 and alpha is >0, span is 0.
            // If distance > 0, pow(distance, alpha).
            if (Math.abs(distance) < EPSILON && Math.abs(alpha) < EPSILON) {
                segmentParamSpans[i] = 1.0; // Convention for 0^0, ensures some span
            } else if (Math.abs(distance) < EPSILON) {
                segmentParamSpans[i] = 0.0; // 0^alpha (alpha > 0) is 0
            }
            else {
                segmentParamSpans[i] = Math.pow(distance, alpha);
            }
            totalParamSpan += segmentParamSpans[i];
        }
        
        // 5. Determine which segment 't_global' falls into and the local 't' for that segment
        
        double localT = 1f;
        int p_idx_for_formula; // Index for P0 in the Catmull-Rom formula (from catmullRomCPs array)
        
        if (t_global >= 1.0 - EPSILON) { // Handle t_global=1.0 case
            p_idx_for_formula = numSegments -1; // Index for the P0 of the segment involving the last actual point
            // This means using catmullRomCPs[numSegments-1], [numSegments], [numSegments+1], [numSegments+2]
            // Which maps to catmullRomCPs[numActualPoints-2], [numActualPoints-1], [numActualPoints], [numActualPoints+1]
            localT = 1.0;
        } else if (t_global < EPSILON) { // Handle t_global = 0.0 case
            p_idx_for_formula = 0;
            localT = 0.0;
        }
        else {
            if (Math.abs(totalParamSpan) < EPSILON) {
                // All segments have zero effective length, fallback to uniform distribution
                // This is similar to original code's segment finding logic
                double scaled_t_uniform = t_global * numSegments;
                int segmentNum_uniform = (int) Math.floor(scaled_t_uniform);
                // Ensure segmentNum is within bounds, especially if t_global is exactly 1.0
                segmentNum_uniform = Math.min(segmentNum_uniform, numSegments - 1);
                localT = scaled_t_uniform - segmentNum_uniform;
                p_idx_for_formula = segmentNum_uniform;
            } else {
                double accumulatedSpan = 0;
                int currentSegment = 0;
                for (int i = 0; i < numSegments; i++) {
                    double normalizedSegmentSpan = segmentParamSpans[i] / totalParamSpan;
                    if (t_global <= accumulatedSpan + normalizedSegmentSpan + EPSILON) { // Add EPSILON for float point safety
                        currentSegment = i;
                        if (Math.abs(normalizedSegmentSpan) < EPSILON) { // Zero length segment
                            localT = 0; // Or 0.5 or 1, behavior might need refinement for zero-length segments
                        } else {
                            localT = (t_global - accumulatedSpan) / normalizedSegmentSpan;
                        }
                        break;
                    }
                    accumulatedSpan += normalizedSegmentSpan;
                }
                // Ensure localT is clamped, especially due to potential floating point inaccuracies
                localT = Math.max(0.0, Math.min(1.0, localT));
                p_idx_for_formula = currentSegment;
            }
        }
        
        // The Catmull-Rom formula uses 4 control points: P0, P1, P2, P3.
        // For a segment starting at actualPoints[k] (which is catmullRomCPs[k+1]),
        // the 4 CPs are:
        // P0_formula = catmullRomCPs[k]
        // P1_formula = catmullRomCPs[k+1] (actualPoints[k])
        // P2_formula = catmullRomCPs[k+2] (actualPoints[k+1])
        // P3_formula = catmullRomCPs[k+3]
        // So, p_idx_for_formula is the index k for catmullRomCPs.
        
        Vec3d P0 = catmullRomCPs[p_idx_for_formula];
        Vec3d P1 = catmullRomCPs[p_idx_for_formula + 1];
        Vec3d P2 = catmullRomCPs[p_idx_for_formula + 2];
        Vec3d P3 = catmullRomCPs[p_idx_for_formula + 3];
        
        // 6. Apply Catmull-Rom interpolation formula (same as before)
        // Q(t) = 0.5 * ( (2*P1) + (-P0+P2)*t + (2*P0-5*P1+4*P2-P3)*t^2 + (-P0+3*P1-3*P2+P3)*t^3 )
        double t2 = localT * localT;
        double t3 = t2 * localT;
        
        Vec3d term1_val = P1.multiply(2.0);
        Vec3d term2_factor = P2.subtract(P0);
        Vec3d term2_val = term2_factor.multiply(localT);
        Vec3d t3_p0 = P0.multiply(2.0);
        Vec3d t3_p1 = P1.multiply(-5.0);
        Vec3d t3_p2 = P2.multiply(4.0);
        Vec3d t3_p3 = P3.multiply(-1.0);
        Vec3d term3_factor = t3_p0.add(t3_p1).add(t3_p2).add(t3_p3);
        Vec3d term3_val = term3_factor.multiply(t2);
        Vec3d t4_p0 = P0.multiply(-1.0);
        Vec3d t4_p1 = P1.multiply(3.0);
        Vec3d t4_p2 = P2.multiply(-3.0);
        Vec3d term4_factor = t4_p0.add(t4_p1).add(t4_p2).add(P3);
        Vec3d term4_val = term4_factor.multiply(t3);
        
        Vec3d result = term1_val.add(term2_val).add(term3_val).add(term4_val);
        return result.multiply(0.5);
    }
    
    /**
     * Helper to get a point on a Chordal Catmull-Rom spline (alpha = 1.0).
     */
    public static Vec3d getPointOnChordalCatmullRomSpline(float progress,
                                                          Vec3d startPoint, Vec3d startTangent,
                                                          Vec3d endPoint, Vec3d endTangent,
                                                          double tangentScale, // e.g., 2.0 for standard, 10.0 for your original feel
                                                          Vec3d... midPoints) {
        return getPointOnCatmullRomSpline(progress, startPoint, startTangent, endPoint, endTangent, 1.0, tangentScale, midPoints);
    }
    
    /**
     * Helper to get a point on a Centripetal Catmull-Rom spline (alpha = 0.5).
     */
    public static Vec3d getPointOnCentripetalCatmullRomSpline(float progress,
                                                              Vec3d startPoint, Vec3d startTangent,
                                                              Vec3d endPoint, Vec3d endTangent,
                                                              double tangentScale,
                                                              Vec3d... midPoints) {
        return getPointOnCatmullRomSpline(progress, startPoint, startTangent, endPoint, endTangent, 0.5, tangentScale, midPoints);
    }
    
    /**
     * Helper to get a point on a Uniform Catmull-Rom spline (alpha = 0.0).
     * This should behave like your original code if tangentScale was effectively 10.0.
     */
    public static Vec3d getPointOnUniformCatmullRomSpline(float progress,
                                                          Vec3d startPoint, Vec3d startTangent,
                                                          Vec3d endPoint, Vec3d endTangent,
                                                          double tangentScale,
                                                          Vec3d... midPoints) {
        return getPointOnCatmullRomSpline(progress, startPoint, startTangent, endPoint, endTangent, 0.0, tangentScale, midPoints);
    }
}