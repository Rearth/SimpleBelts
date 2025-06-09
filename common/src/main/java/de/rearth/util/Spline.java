package de.rearth.util;

import net.minecraft.util.math.Vec3d;

public class Spline {
    
    /**
     * Calculates a point on a Catmull-Rom spline using Minecraft's Vec3d.
     * The spline passes through startPoint, all midPoints, and endPoint.
     * Tangents at start and end are controlled by startTangent and endTangent.
     *
     * @param progress     Progress along the entire spline (0.0 to 1.0).
     * @param startPoint   The starting point of the spline.
     * @param startTangent The tangent vector (direction * strength) at the startPoint.
     * @param endPoint     The ending point of the spline.
     * @param endTangent   The tangent vector (direction * strength) at the endPoint.
     * @param midPoints    Optional array of points the spline must pass through between start and end.
     * @return The Vec3d point on the spline corresponding to the given progress.
     */
    public static Vec3d getPointOnCatmullRomSpline(float progress,
                                                   Vec3d startPoint, Vec3d startTangent,
                                                   Vec3d endPoint, Vec3d endTangent,
                                                   Vec3d... midPoints) {
        
        // 1. Clamp progress
        double t_global = Math.max(0.0, Math.min(1.0, progress));
        
        // 2. Determine number of midpoints (handle null case)
        int numMidPoints = (midPoints == null) ? 0 : midPoints.length;
        int numActualPoints = numMidPoints + 2; // start + mids + end
        
        // 4. Construct the full array of control points for Catmull-Rom.
        // These are P_minus_1, P_0, P_1, ..., P_N, P_N_plus_1
        // Where P_0 to P_N are the actual points the spline passes through.
        Vec3d[] catmullRomCPs = new Vec3d[numActualPoints + 2];
        
        // Define P_0 (actual start point)
        catmullRomCPs[1] = startPoint;
        
        // Define P_N (actual end point)
        catmullRomCPs[numMidPoints + 2] = endPoint; // This is catmullRomCPs[numActualPoints]
        
        // Define intermediate points P_1..P_N-1
        if (midPoints != null)
            System.arraycopy(midPoints, 0, catmullRomCPs, 2, numMidPoints);
        
        // Define P_minus_1 (phantom point before start to control start tangent)
        // P_minus_1 = P_0 - startTangent * (factor, often 0.5 or 1 depending on desired tension effect)
        // The standard Catmull-Rom tangent at P_0 is 0.5 * (P_1 - P_minus_1).
        // If we want this tangent to be startTangent:
        // startTangent = 0.5 * (P_1 - P_minus_1)
        // 2 * startTangent = P_1 - P_minus_1
        // P_minus_1 = P_1 - 2 * startTangent
        // P_1 is the point after startPoint (catmullRomCPs[2], or endPoint if no mids)
        Vec3d pointAfterStart = (numMidPoints > 0) ? catmullRomCPs[2] : catmullRomCPs[numActualPoints]; // This is P1 in CR terms
        catmullRomCPs[0] = pointAfterStart.subtract(startTangent.multiply(45.0));
        
        
        // Define P_N_plus_1 (phantom point after end to control end tangent)
        // P_N_plus_1 = P_N + endTangent * (factor)
        // Tangent at P_N is 0.5 * (P_N_plus_1 - P_N-1).
        // If we want this to be endTangent:
        // endTangent = 0.5 * (P_N_plus_1 - P_N-1)
        // 2 * endTangent = P_N_plus_1 - P_N-1
        // P_N_plus_1 = P_N-1 + 2 * endTangent
        // P_N-1 is the point before endPoint (catmullRomCPs[numActualPoints-1], or startPoint if no mids)
        Vec3d pointBeforeEnd = (numMidPoints > 0) ? catmullRomCPs[numActualPoints - 1] : catmullRomCPs[1]; // This is P_N-1 in CR terms
        catmullRomCPs[numActualPoints + 1] = pointBeforeEnd.add(endTangent.multiply(45.0));
        
        
        // 5. Determine which segment 't_global' falls into and the local 't' for that segment
        int numSegments = numActualPoints - 1;
        
        double localT;
        int p0_idx; // Index for P0 in the Catmull-Rom formula (from catmullRomCPs array)
        
        if (t_global >= 1.0) { // Handle t_global=1.0 case to ensure it lands on the last actual point
            // The last segment uses control points ending at catmullRomCPs[numActualPoints+1]
            // Its P0 is catmullRomCPs[numActualPoints-1]
            p0_idx = numActualPoints - 1;
            localT = 1.0;
        } else {
            double scaled_t = t_global * numSegments;
            int segmentNum = (int) Math.floor(scaled_t); // 0 to numSegments-1
            localT = scaled_t - segmentNum;
            p0_idx = segmentNum + 1; // P0 for the formula is catmullRomCPs[segmentNum + 1]
            // No, P0 for formula is catmullRomCPs[actual_point_index_for_segment_start - 1 + 1]
            // e.g. first segment (segmentNum=0) starts at actual point 0 (catmullRomCPs[1])
            // so its P0 for formula is catmullRomCPs[0].
            // P1 is catmullRomCPs[1], P2 is catmullRomCPs[2], P3 is catmullRomCPs[3]
            // So for segment `segmentNum`, P0_formula = catmullRomCPs[segmentNum]
            p0_idx = segmentNum; // Corrected: index for P0 of the 4 CPs for Catmull-Rom formula
        }
        
        Vec3d P0 = catmullRomCPs[p0_idx];
        Vec3d P1 = catmullRomCPs[p0_idx + 1];
        Vec3d P2 = catmullRomCPs[p0_idx + 2];
        Vec3d P3 = catmullRomCPs[p0_idx + 3];
        
        // 6. Apply Catmull-Rom interpolation formula
        // Q(t) = 0.5 * ( (2*P1) + (-P0+P2)*t + (2*P0-5*P1+4*P2-P3)*t^2 + (-P0+3*P1-3*P2+P3)*t^3 )
        
        double t2 = localT * localT;
        double t3 = t2 * localT;
        
        // Term 1: 0.5 * (2*P1) = P1
        Vec3d term1_val = P1.multiply(2.0);
        
        // Term 2: 0.5 * (-P0+P2)*t
        Vec3d term2_factor = P2.subtract(P0);
        Vec3d term2_val = term2_factor.multiply(localT);
        
        // Term 3: 0.5 * (2*P0-5*P1+4*P2-P3)*t^2
        Vec3d t3_p0 = P0.multiply(2.0);
        Vec3d t3_p1 = P1.multiply(-5.0);
        Vec3d t3_p2 = P2.multiply(4.0);
        Vec3d t3_p3 = P3.multiply(-1.0);
        Vec3d term3_factor = t3_p0.add(t3_p1).add(t3_p2).add(t3_p3);
        Vec3d term3_val = term3_factor.multiply(t2);
        
        // Term 4: 0.5 * (-P0+3*P1-3*P2+P3)*t^3
        Vec3d t4_p0 = P0.multiply(-1.0);
        Vec3d t4_p1 = P1.multiply(3.0);
        Vec3d t4_p2 = P2.multiply(-3.0);
        // P3 is just P3
        Vec3d term4_factor = t4_p0.add(t4_p1).add(t4_p2).add(P3);
        Vec3d term4_val = term4_factor.multiply(t3);
        
        Vec3d result = term1_val.add(term2_val).add(term3_val).add(term4_val);
        return result.multiply(0.5);
    }
    
}
