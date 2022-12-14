
import com.neuronrobotics.bowlerstudio.physics.TransformFactory
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR
import com.neuronrobotics.sdk.addons.kinematics.DHChain;
import com.neuronrobotics.sdk.addons.kinematics.DhInverseSolver;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import java.text.DecimalFormat
import java.util.ArrayList;

import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.common.Log;
import Jama.Matrix;
import javafx.application.Platform
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class deltaIK implements DhInverseSolver {
	boolean debug = false;

	int limbIndex =0;

	@Override
	public double[] inverseKinematics(TransformNR target, double[] jointSpaceVector, DHChain chain) {
		return inverseKinematics6dof(target,jointSpaceVector,chain);
	}
	TransformNR linkOffset(DHLink link) {
		return new TransformNR(link.DhStep(0));
	}
	double length(TransformNR tr) {
		return Math.sqrt(
				Math.pow(tr.getX(), 2)+
				Math.pow(tr.getY(), 2)+
				Math.pow(tr.getZ(), 2)
				);
	}
	private TransformNR toolOffset(int linkNum,ArrayList<DHLink> links, double[] jointSpaceVector) {
		if(linkNum==8) {
			//println "All Tool frames"
		}
		TransformNR wristCenterOffsetTransform =new TransformNR();
		for(int i=5;i<linkNum;i++) {
			TransformNR linkOffset = linkOffset(links.get(i))
			if(i==7){
				//linkOffset=new TransformNR(links.get(i).DhStep(Math.toRadians(jointSpaceVector[i])));
			}
			//if(linkNum==8) println "linkOffset "+i+" "+linkOffset
			wristCenterOffsetTransform=wristCenterOffsetTransform.times(linkOffset)
			//if(linkNum==8) println "wristCenterOffsetTransform "+wristCenterOffsetTransform
		}
		return wristCenterOffsetTransform
	}

	public double[] inverseKinematics6dof(TransformNR target, double[] jointSpaceVector, DHChain chain) {
		int linkNum = jointSpaceVector.length;
		double[] current = new double[linkNum]
		for(int i=0;i<linkNum;i++) {
			current[i]=jointSpaceVector[i];
		}
		ArrayList<DHLink> links = chain.getLinks();
		
		TransformNR l0Offset = linkOffset(links.get(0));
		TransformNR l1Offset = linkOffset(links.get(1));
		TransformNR l2Offset = linkOffset(links.get(2));
		TransformNR l3Offset = linkOffset(links.get(3));
		// Vector decompose the tip target
		double z = target.getZ();
		double y = target.getY();
		double x = target.getX();
		// delta kinematics has no rotation at this layer
		//target =new TransformNR(x,y,z,new RotationNR(0,-180,0));

		RotationNR q = target.getRotation();
		if(linkNum>7) {
			TransformNR rot = new TransformNR(0,0,0,new RotationNR(0,0,90)).times(new TransformNR(0,0,0,q));
			double az=Math.toDegrees(rot.getRotation().getRotationAzimuth())-90
			double el=Math.toDegrees(rot.getRotation().getRotationElevation())
			double tlt=Math.toDegrees(rot.getRotation().getRotationTilt())
			if(az<-180)
				az+=360;
			if(az>180)
				az-=360
			//println az+ " "+el+" "+tlt
			jointSpaceVector[7]=-az+180
			target =new TransformNR(x,y,z,new RotationNR(0.7071067811865476, 0, 0.7071067811865475, 0));
		}

		TransformNR newCenter =target.copy();
		// Start by finding the IK to the wrist center
		if(linkNum>=6) {
			// compute the transform from tip to wrist center
			TransformNR wristCenterOffsetTransform =toolOffset(linkNum,links,jointSpaceVector)
			try {
				//if(linkNum==8) println "\n target "+target
				//println wristCenterOffsetTransform
				//System.out.println( wristCenterOffsetTransform
				// take off the tool from the target to get the center of the wrist
				TransformNR wristCenterOffsetTransformInverse = wristCenterOffsetTransform.inverse()
				//if(linkNum==8) println " wristCenterOffsetTransformInverse "+wristCenterOffsetTransformInverse
				def tmp =target.times(wristCenterOffsetTransformInverse);
				//newCenter =tmp
				newCenter.translateX(-wristCenterOffsetTransformInverse.getZ());
				newCenter.translateY(-wristCenterOffsetTransformInverse.getX());
				newCenter.translateZ(wristCenterOffsetTransformInverse.getY());
				//if(linkNum==8) println " new center "+tmp
				//if(linkNum==8) println "\n\n"
			}catch(Throwable t) {
				t.printStackTrace(System.out)
			}
		}
		
		// recompute the X,y,z with the new center
		z = newCenter.getZ();
		y = newCenter.getY();
		x = newCenter.getX();
		//xyz now are at the wrist center
		// Compute the xy plane projection of the tip
		// this is the angle of the tipto the base link
		if(x==0&&y==0) {
			System.out.println( "Singularity! try something else");
			return inverseKinematics6dof(target.copy().translateX(0.01),jointSpaceVector,chain);
		}
		if(debug)System.out.println( "Wrist center for IK "+x+","+y+","+z);
		double baseVectorAngle = Math.toDegrees(Math.atan2(y , x));
		double elbowLink1CompositeLength = length(l1Offset);
		double elbowLink2CompositeLength=length(l3Offset);
		double wristVect = length(newCenter);
		if(debug)System.out.println( "elbowLink1CompositeLength "+elbowLink1CompositeLength);
		if(debug)System.out.println( "elbowLink2CompositeLength "+elbowLink2CompositeLength);
		if(debug)System.out.println( "Elbo Hypotinuse "+wristVect);
		double elbowTiltAngle =-( Math.toDegrees(
				Math.acos(
				(
				Math.pow(elbowLink2CompositeLength,2)+
				Math.pow(elbowLink1CompositeLength,2)
				-Math.pow(wristVect,2)
				)
				/
			(2*elbowLink2CompositeLength*elbowLink1CompositeLength)
			)
			));
		if(debug)System.out.println( "Elbow angle "+elbowTiltAngle);
		jointSpaceVector[2]=elbowTiltAngle - Math.toDegrees(links.get(2).getTheta());
		
		TransformNR local = new TransformNR(0,0,0,new RotationNR(0, -baseVectorAngle, 0));
		TransformNR tipOnXVect = local.times(newCenter);
		double elZ = tipOnXVect.getZ();
		double elX = tipOnXVect.getX();
		double L1 = length(l1Offset);
		double L2 = length(l3Offset);
		
		if(debug)System.out.println( "L1 "+L1+" l2 "+L2+" z "+elZ+" x "+elX);
		/**
		* System of equasions
		* Theta2 = asin(z/wristVect)
		* l3 = wristVect * cos( theta2)
		* theta1 = acos(l1^2+x^2-l3^2/2*l1*x)
		*
		*/
		double asinVal = elZ/L2;
		if(asinVal>1 || asinVal<-1)
			throw new RuntimeException("Target outside workspace, passive links too short to reach "+L2);
		double theta2 = Math.asin(asinVal);

		double L3 = L2*Math.cos(theta2);
		double theta1 = Math.acos(
				(
				Math.pow(L1, 2) +
				Math.pow(elX, 2)-
				Math.pow(L3, 2)
				)/
				(2 * L1 *elX)
				);
		jointSpaceVector[0]=-(90-(Math.toDegrees(theta1)+baseVectorAngle));
		TransformNR reorent;
		try {
			reorent =new TransformNR(0,0,0,new RotationNR(0, -jointSpaceVector[0], 0));
		}catch (Throwable t){
			//t.printStackTrace()
			throw new RuntimeException( "error calculating base angle: \nL1 "+L1+
			" \nl2 "+L2+
			" \nz "+elZ+
			" \nx "+elX+
			" \nl3 "+L3+
			" \ntheta2 "+Math.toDegrees(theta2)+
			" \nasinVal "+asinVal

			);
		}
		TransformNR sphericalElbowTartget = reorent.times(newCenter);
		//System.out.println( newCenter
		//System.out.println( 	sphericalElbowTartget
		sphericalElbowTartget = new TransformNR(0.0,-sphericalElbowTartget.getY(),0.0, new RotationNR()).times(sphericalElbowTartget);
		//System.out.println( 	sphericalElbowTartget
		double theta3 = Math.atan2(sphericalElbowTartget.getZ(), sphericalElbowTartget.getX());
		jointSpaceVector[1]=-Math.toDegrees(theta3) ;

		//return jointSpaceVector

		/**
		 // compute the top of the wrist now that the first 3 links are calculated
		 * 
		 */
		double[] wristLinks=new double[jointSpaceVector.length];
		for(int i=0;i<3;i++) {
			wristLinks[i]=jointSpaceVector[i];
		}
		for(int i=3;i<jointSpaceVector.length;i++) {
			wristLinks[i]=0;
		}
		ArrayList<TransformNR> chainToLoad =new ArrayList<>();
		chain.forwardKinematicsMatrix(wristLinks,chainToLoad);
		TransformNR	startOfWristSet=chain.kin.inverseOffset(chainToLoad.get(2));
		TransformNR virtualcenter = newCenter.times(new TransformNR(0,0,10,
				new RotationNR(Math.toDegrees(links.get(5).getAlpha()),0,0)));
		TransformNR wristMOvedToCenter0 =startOfWristSet
				.inverse()// move back from base ot wrist to world home
				.times(virtualcenter);// move forward to target, leaving the angle between the tip and the start of the rotation
		//if(debug)System.out.println( 	wristMOvedToCenter0
		RotationNR qWrist=wristMOvedToCenter0.getRotation();
		if(wristMOvedToCenter0.getX()==0&&wristMOvedToCenter0.getY()==0) {
			System.out.println( "Singularity! try something else");
			return inverseKinematics6dof(target.copy().translateX(0.01),jointSpaceVector,chain);
		}
		double closest= (Math.toDegrees(Math.atan2(wristMOvedToCenter0.getY(), wristMOvedToCenter0.getX()))-Math.toDegrees(links.get(3).getTheta()));

		jointSpaceVector[3]=closest;
		wristLinks[3]=jointSpaceVector[3];
		if(jointSpaceVector.length==4)
			return jointSpaceVector;

		chainToLoad =new ArrayList<>();
		/**
		 // Calculte the second angle
		 * 
		 */
		chainToLoad.clear();
		chain.forwardKinematicsMatrix(wristLinks,chainToLoad);
		TransformNR	startOfWristSet2=chain.kin.inverseOffset(chainToLoad.get(3));

		TransformNR wristMOvedToCenter1 =startOfWristSet2
				.inverse()// move back from base ot wrist to world home
				.times(virtualcenter);// move forward to target, leaving the angle between the tip and the start of the rotation
		//if(debug)System.out.println( " Middle link ="	+wristMOvedToCenter1
		RotationNR qWrist2=wristMOvedToCenter1.getRotation();
		if(wristMOvedToCenter1.getX()==0&&wristMOvedToCenter1.getY()==0) {
			System.out.println( "Singularity! try something else");
			return inverseKinematics6dof(target.copy().translateX(0.01),jointSpaceVector,chain);
		}
		jointSpaceVector[4]=(Math.toDegrees(Math.atan2(wristMOvedToCenter1.getY(), wristMOvedToCenter1.getX()))-
				Math.toDegrees(links.get(4).getTheta())-
				90);
		wristLinks[4]=jointSpaceVector[4];
		if(jointSpaceVector.length==5)
			return jointSpaceVector;
		chainToLoad =new ArrayList<>();
		/**
		 // Calculte the last angle
		 * 
		 */
		chain.forwardKinematicsMatrix(wristLinks,chainToLoad);
		TransformNR	startOfWristSet3=chain.kin.inverseOffset(chainToLoad.get(4));

		TransformNR wristMOvedToCenter2 =startOfWristSet3
				.inverse()// move back from base ot wrist to world home
				.times(target
					//.times(toolOffset(linkNum,links,jointSpaceVector).inverse())
					);// move forward to target, leaving the angle between the tip and the start of the rotation
		//if(debug)System.out.println( "\n\nLastLink "	+wristMOvedToCenter2
		RotationNR qWrist3=wristMOvedToCenter2.getRotation();
		jointSpaceVector[5]=(Math.toDegrees(qWrist3.getRotationAzimuth())-Math.toDegrees(links.get(5).getTheta()));

		double[] nrm = WristNormalizer.normalize([
			jointSpaceVector[3],
			jointSpaceVector[4],
			jointSpaceVector[5]
		]as double[],
		[
			current[3],
			current[4],
			current[5]
		]as double[],
		chain);
		jointSpaceVector[3]=nrm[0]
		jointSpaceVector[4]=nrm[1]
		jointSpaceVector[5]=nrm[2]

		return jointSpaceVector;
	}
	

	public static double[] normalize(double[] calculated, double[] current, DHChain chain) {
		AbstractKinematicsNR kin = chain.kin;
		// DecimalFormat df = new DecimalFormat("000.00");
		double[] alt1 =option( calculated[0] - 180, -calculated[1], calculated[2] - 180 );
		double[] calculated2 =option( calculated[0] + 360, calculated[1] + 360, calculated[2] + 360 );
		double[] calculated3 =option( calculated[0] - 360, calculated[1] - 360, calculated[2] - 360 );
		double[] alt2 =option( alt1[0] + 360, alt1[1] + 360, alt1[2] + 360 );
		double[] alt3 =option( alt1[0] - 360, alt1[1] - 360, alt1[2] - 360 );
		double[] calculated6 =option( calculated[0] - 360, calculated[1], calculated[2] );
		double[] calculated7 =option( calculated[0] + 360, calculated[1], calculated[2] );
		double[] als4 =option( alt1[0] - 360, alt1[1], alt1[2] );
		double[] alt5 =option( alt1[0] + 360, alt1[1], alt1[2] );



		HashMap<double[], Double> scores = new HashMap<>();
		score(calculated, current, scores, kin);
		score(alt1, current, scores, kin);
		score(calculated2, current, scores, kin);
		score(calculated3, current, scores, kin);
		score(alt2, current, scores, kin);
		score(alt3, current, scores, kin);
		score(calculated6, current, scores, kin);
		score(calculated7, current, scores, kin);
		score(als4, current, scores, kin);
		score(alt5, current, scores, kin);
		score(option( calculated[0] -180, -calculated[1], calculated[2]+180 ), current, scores, kin);
		score(option( alt1[0] -180, alt1[1], -alt1[2]+180 ), current, scores, kin);
		score(option( calculated[0] +180, -calculated[1], calculated[2]-180 ), current, scores, kin);
		score(option( alt1[0] +180, -alt1[1], alt1[2]-180 ), current, scores, kin);

		if (scores.size() > 0) {
			double[] start =calculated ;
			if(scores.get(start)==null) {
				start = (double[]) scores.keySet().toArray()[0];
			}
			double score=scores.get(start);
			double[] ret = calculated;
			for (double[] tmp : scores.keySet()) {
				double delt = scores.get(tmp);
				println tmp+" score "+delt
				if (delt < score) {
					score = delt;
					ret = tmp;
					println "Best Yet"
				}
			}
			scores.clear();

			return ret;
		}
		throw new RuntimeException("No Wrist Solution! ");
	}
	private static double[] option(double w1,double w2,double w3) {
		return [w1, w2, w3] as double[];
	}

	private static void score(double[] calculated, double[] current, HashMap<double[], Double> scores,
			AbstractKinematicsNR kin) {
		double delt = 0;
		for (int i = 0; i < 3; i++) {
			int i3 = i + 3;
			calculated[i] = calculated[i] % 360;

			if (calculated[i] > kin.getMaxEngineeringUnits(i3)) {
				return;
			}
			if (calculated[i] < kin.getMinEngineeringUnits(i3)) {
				return;
			}
			double measure = current[i] - calculated[i];
			if (Math.abs(measure) > Math.abs(delt)) {
				delt = measure;
			}
		}
		scores.put(calculated, Math.abs(delt));
	}
}


return new deltaIK()

