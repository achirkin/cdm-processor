package ch.ethz.arch.ia.cdm.parse;

//import static org.bytedeco.javacpp.helper.opencv_core.CV_RGB;
//import static org.bytedeco.javacpp.helper.opencv_core.cvDrawContours;
import static org.bytedeco.javacpp.helper.opencv_imgproc.cvFindContours;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvGet1D;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_RGB2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_CHAIN_APPROX_SIMPLE;
import static org.bytedeco.javacpp.opencv_imgproc.CV_POLY_APPROX_DP;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RETR_TREE;
import static org.bytedeco.javacpp.opencv_imgproc.cvApproxPoly;
import static org.bytedeco.javacpp.opencv_imgproc.cvArcLength;
import static org.bytedeco.javacpp.opencv_imgproc.cvContourArea;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.CvContour;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvPoint;
import org.bytedeco.javacpp.opencv_core.CvScalar;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.helper.opencv_core.CvArr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

import org.bytedeco.javacpp.opencv_core.*;

public class ParserHelpers {
	
	private static final Logger log = LoggerFactory.getLogger(ParserHelpers.class);
	public static final boolean DEBUG = false;

//	/***
//	 * rotate image if necessary
//	 * @param image
//	 * @param release if to release old image or not
//	 * @return portrait-oriented image
//	 */
//	public static IplImage toPortrait(IplImage image, boolean release){
//		if (image.width() <= image.height())
//			return image;
//		IplImage nimg = IplImage.create(image.height(), image.width(), image.depth(), image.nChannels());
//		cvTranspose(image, nimg);
//		cvFlip(nimg);
//		if(release)
//			image.release();
//		return nimg;
//	}
	
	
	/***
	 * Finds QR code edges
	 * @param contour
	 * @param eps - relative precision (in [0,1]). It is ok to have something like 0.2
	 * @return Three contours in a sequence in the perfect case.
	 */
	public static CvSeq findQRCornerCandidates(CvSeq contour, double eps){
		
		LinkedList<CvSeq> c2Proc = new LinkedList<CvSeq>();
//		LinkedList<CvSeq> candidates = new LinkedList<CvSeq>();
		HashMap<CvSeq, Double> candidates = new HashMap<CvSeq, Double>();
		c2Proc.add(contour);
		while(!c2Proc.isEmpty()){
			CvSeq cr = c2Proc.remove();
			// go width-first
			if(cr.h_next() != null)
				c2Proc.add(cr.h_next());
			// check if current contour satisfies conditions
			if(cr.v_next() != null){
				if (cr.v_next().v_next() != null) {
					double a = cvContourArea(cr),
						   b = cvContourArea(cr.v_next()),
						   c = cvContourArea(cr.v_next().v_next()),
						   r = Math.max(Math.abs(a*5*5-b*7*7)/a/25, Math.abs(a*3*3-c*7*7)/a/9);
						   
	//				System.out.println(3*5*3*5 + " - " +  b*3*7*3*7/a + " - " + c*5*7*5*7/a);
					if(r < eps)
					{
	//					System.out.println(Math.abs(a*5*5-b*7*7)/a/25);
	//					System.out.println(Math.abs(a*3*3-c*7*7)/a/9);
						cr.h_next(null);
						cr.v_next(null);
						cr.h_prev(null);
						cr.v_prev(null);
						candidates.put(cr, -r);
						continue;
					}
				}
				c2Proc.add(cr.v_next());
			}
			
		}
		
		if(candidates.size() < 3)
			if(eps < 0.5)
				return findQRCornerCandidates(contour, eps + 0.1);
			else
				throw new IllegalArgumentException("Could not find enough QR corner candidates.");
		if(DEBUG){
			System.out.println(candidates.size() + " - ");
			candidates.forEach((c,v) -> System.out.print(v + " "));
		}
		
		ArrayList<CvSeq> sortedCandidates = sortByValue(candidates);
		sortedCandidates.get(1).h_next(sortedCandidates.get(2));
		sortedCandidates.get(0).h_next(sortedCandidates.get(1));
		return sortedCandidates.get(0);
	}
	
	
	/***
	 * 
	 * @param binImg
	 * @return four corners of QR code
	 */
	public static CvPoint findQRpoints(IplImage binImg){
		
		if(DEBUG)
			log.debug("Looking for QR codes...");
		CvSeq QRcontours = new CvSeq();
		if(DEBUG)
			cvSaveImage("bin.jpg", binImg);
		CvMemStorage QRcontourStorage = CvMemStorage.create();
		cvFindContours(binImg, QRcontourStorage, QRcontours, Loader.sizeof(CvContour.class), CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE);
		CvSeq QRcontour = ParserHelpers.findQRCornerCandidates(QRcontours, 0.2);
		IplImage cImg = null;
		if(DEBUG){
			cImg = IplImage.create(binImg.cvSize(), IPL_DEPTH_8U, 3);
			cvCvtColor(binImg, cImg, COLOR_GRAY2RGB);
			cvDrawContours(cImg, QRcontours, CV_RGB(100, 0, 0), CV_RGB(0, 0, 100), 5, 3, 8);
			cvSaveImage("contours.jpg", cImg);
		
			cImg.release();
			cImg = IplImage.create(binImg.cvSize(), IPL_DEPTH_8U, 3);
			cvCvtColor(binImg, cImg, COLOR_GRAY2RGB);
			cvDrawContours(cImg, QRcontour, CV_RGB(100, 0, 0), CV_RGB(0, 0, 100), 5, 3, 8);
			cvSaveImage("contour.jpg", cImg);
		}
		
		//cvDrawContours(binImg, QRcontour, CV_RGB(100, 0, 0), CV_RGB(0, 0, 100), 5, 3, 8);
		
		////////////////////////////
		CvSeq poly1 = cvApproxPoly(QRcontour, Loader.sizeof(CvContour.class), QRcontourStorage, CV_POLY_APPROX_DP, cvArcLength(QRcontour)*0.02);
		CvSeq poly2 = cvApproxPoly(QRcontour.h_next(), Loader.sizeof(CvContour.class), QRcontourStorage, CV_POLY_APPROX_DP, cvArcLength(QRcontour.h_next())*0.02);
		CvSeq poly3 = cvApproxPoly(QRcontour.h_next().h_next(), Loader.sizeof(CvContour.class), QRcontourStorage, CV_POLY_APPROX_DP, cvArcLength(QRcontour.h_next().h_next())*0.02);
		log.debug("Number of points in three QR code contours: " + poly1.total() + ", " + poly2.total() + ", " + poly3.total());
		//cvDrawContours(binImg, QRcontour, CV_RGB(255,255,255), CV_RGB(0, 0, 255), 5, 3, 8);
		//cvDrawContours(binImg, poly2, CV_RGB(255,255,255), CV_RGB(0, 0, 255), 5, 3, 8);
		//cvDrawContours(binImg, poly3, CV_RGB(255,255,255), CV_RGB(0, 0, 255), 5, 3, 8);
		
		CvSeq main = null, bottom = null, right = null;
		int mi = 0, bi = 0, ri = 0;
		{
			int[] i12 = farestPoints(poly1, poly2);
			int[] i13 = farestPoints(poly1, poly3);
			int[] i23 = farestPoints(poly2, poly3);
//			System.out.println(i12[0] + " - " + i12[1]);
//			System.out.println(i13[0] + " - " + i13[1]);
//			System.out.println(i23[0] + " - " + i23[1]);
			double d12 = distanceSquared(p(poly1,i12[0]), p(poly2,i12[1]));
			double d13 = distanceSquared(p(poly1,i13[0]), p(poly3,i13[1]));
			double d23 = distanceSquared(p(poly2,i23[0]), p(poly3,i23[1]));

//			System.out.println(d12 + " - " + d13 + " - " + d23);
			CvSeq lr1 = null, lr2 = null;
			int ilr1 = 0, ilr2 = 0;
			if (d12 > d23 && d12 > d13){
				main = poly3;
				lr1 = poly1;
				lr2 = poly2;
				ilr1 = i12[0];
				ilr2 = i12[1];
			} else if (d23 > d12 && d23 > d13){
				main = poly1;
				lr1 = poly2;
				lr2 = poly3;
				ilr1 = i23[0];
				ilr2 = i23[1];
			} else {
				main = poly2;
				lr1 = poly1;
				lr2 = poly3;
				ilr1 = i13[0];
				ilr2 = i13[1];
			}
			CvPoint cvec = new CvPoint();
			sum(p(lr1,ilr1), p(lr2,ilr2), cvec);
			mult(cvec, 0.5f);
			mi = farestPoint(main, cvec);
			
			CvPoint d1 = new CvPoint();
			CvPoint d2 = new CvPoint();
			sub(p(main,mi),p(lr1,ilr1),d1);
			sub(p(main,mi),p(lr2,ilr2),d2);
			if(cross(d1,d2) > 0){
				bottom = lr2; bi = ilr2;
				right = lr1; ri = ilr1;
			} else {
				bottom = lr1; bi = ilr1;
				right = lr2; ri = ilr2;
			}
			// now we have found position of all three marks and corners of the QR code
		}
//		cvDrawContours(binImg, main, CV_RGB(255,255,255), CV_RGB(0, 0, 255), 5, 3, 8);
//		cvDrawContours(binImg, bottom, CV_RGB(255,255,255), CV_RGB(0, 0, 255), 5, 3, 8);
//		cvDrawCircle(binImg, p(bottom,bi), 10, CV_RGB(255,255,255), -1, 8, 0);
		
		
		// find secondary points in each label
		int mi2, ri2, bi2;
		{
			mi2 = farestPoint(main, p(main,mi));
			CvPoint vec = new CvPoint();
			sub(p(right,ri),p(main,mi),vec);
			bi2 = farestAlongSide(bottom, p(bottom,bi), vec);
			sub(p(bottom,bi),p(main,mi),vec);
			ri2 = farestAlongSide(right, p(right,ri), vec);
			
		}
//		cvDrawCircle(binImg, p(main,mi2), 10, CV_RGB(150,100,150), -1, 8, 0);
//		cvDrawCircle(binImg, p(bottom,bi2), 10, CV_RGB(150,100,150), -1, 8, 0);
//		cvDrawCircle(binImg, p(right,ri2), 10, CV_RGB(150,100,150), -1, 8, 0);
//		
		
		// find last point - intersection
		CvPoint last = new CvPoint();
		{
			double[] lm = lineEq(p(main,mi2), p(main,mi));
			double[] lb = lineEq(p(bottom,bi2), p(bottom,bi));
			double[] lr = lineEq(p(right,ri2), p(right,ri));
//			System.out.println(lm[0] + " - " + lm[1]);
//			System.out.println(lb[0] + " - " + lb[1]);
//			System.out.println(lr[0] + " - " + lr[1]);
			
//			intersect(lb, lr, last);
			CvPoint l1 = new CvPoint(),
					l2 = new CvPoint(),
					l3 = new CvPoint();
			intersect(lm, lb, l1);
			intersect(lm, lr, l2);
			intersect(lb, lr, l3);
//			cvDrawCircle(binImg, l1, 8, CV_RGB(150,100,150), -1, 8, 0);
//			cvDrawCircle(binImg, l2, 8, CV_RGB(150,100,150), -1, 8, 0);
//			cvDrawCircle(binImg, l3, 8, CV_RGB(150,100,150), -1, 8, 0);
			sum(l1, l2, last);
			sum(l3, last, last);
			mult(last,(float)(1.0/3.0));
		}

//		cvDrawCircle(binImg, last, 8, CV_RGB(150,100,150), -1, 8, 0);


		if(DEBUG){
			cvDrawCircle(cImg, p(main,mi), 10, CV_RGB(255,100,50), -1, 8, 0);
			cvDrawCircle(cImg, p(bottom,bi), 10, CV_RGB(50,255,50), -1, 8, 0);
			cvDrawCircle(cImg, p(right,ri), 10, CV_RGB(50,50,255), -1, 8, 0);
			cvDrawCircle(cImg, last, 10, CV_RGB(255,150,255), -1, 8, 0);
			cvSaveImage("contourPoints.jpg", cImg);
			cImg.release();
		}
		
		
		// copy points
		CvPoint corners = new CvPoint(4);

		// top left
		corners.position(0).put(p(main, mi));
		// top right
		corners.position(1).put(p(right, ri));
		// bottom left
		corners.position(2).put(p(bottom, bi));
		// bottom right
		corners.position(3).put(last);
		QRcontourStorage.release();
		return corners;
	}
	
	
//	private CvMat perspective(CvPoint2D32f src, CvPoint2D32f dst) {
//	    CvMat p = cvCreateMat(2, 4, CV_32FC1);
//	    CvMat h = cvCreateMat(2, 4, CV_32FC1);
//	    CvMat p2h = cvCreateMat(3, 3, CV_32FC1);
//	    cvZero(p);
//	    cvZero(h);
//	    cvZero(p2h);
//
//	    p.put(0, 0, src.position(0).x());
//	    p.put(1, 0, src.position(0).y());
//	    p.put(0, 1, src.position(1).x());
//	    p.put(1, 1, src.position(1).y());
//	    p.put(0, 2, src.position(2).x());
//	    p.put(1, 2, src.position(2).y());
//	    p.put(0, 3, src.position(3).x());
//	    p.put(1, 3, src.position(3).y());
//
//	    
//	    h.put(0, 0, dst.position(0).x());
//	    h.put(1, 0, dst.position(0).y());
//	    h.put(0, 1, dst.position(1).x());
//	    h.put(1, 1, dst.position(1).y());
//	    h.put(0, 2, dst.position(2).x());
//	    h.put(1, 2, dst.position(2).y());
//	    h.put(0, 3, dst.position(3).x());
//	    h.put(1, 3, dst.position(3).y());
//	    
////	    h.put(0, 0, 0);
////	    h.put(1, 0, 0);
////	    h.put(0, 1, src.width());
////	    h.put(1, 1, 0);
////	    h.put(0, 2, 0);
////	    h.put(1, 2, src.height());
////	    h.put(0, 3, src.width());
////	    h.put(1, 3, src.height());
//
//	    cvFindHomography(p, h, p2h);
//	    //cvWarpPerspective(src, src, p2h);
//	    return p2h
//	}
	
	
	
//	private static int[] closestPoints(CvSeq s1, CvSeq s2){
//		int i0 = 0, j0 = 0;
//		double d = Double.POSITIVE_INFINITY, t;
//		for(int i = 0; i < s1.total(); i++)
//			for(int j = 0; j < s2.total(); j++)
//			{
//				t = distanceSquared(new CvPoint(cvGetSeqElem(s1, i)), new CvPoint(cvGetSeqElem(s2, i)) );
//				if (t < d)
//				{
//					d = t;
//					i0 = i;
//					j0 = j;
//				}
//			}
//		return new int[]{i0, j0};
//	}
	
	private static int farestPoint(CvSeq s1, CvPoint p){
		int i0 = 0;
		double d = 0, t;
		for(int i = 0; i < s1.total(); i++)
			{
				t = distanceSquared(p(s1, i), p );
				if (t > d)
				{
					d = t;
					i0 = i;
				}
			}
		return i0;
	}
	
//	private static int closestPoint(CvSeq s1, CvPoint p){
//		int i0 = 0;
//		double d = Double.POSITIVE_INFINITY, t;
//		for(int i = 0; i < s1.total(); i++)
//			{
//				t = distanceSquared(p(s1, i), p );
//				if (t < d)
//				{
//					d = t;
//					i0 = i;
//				}
//			}
//		return i0;
//	}
	
	private static int[] farestPoints(CvSeq s1, CvSeq s2){
		int i0 = 0, j0 = 0;
		double d = 0, t;
		for(int i = 0; i < s1.total(); i++)
			for(int j = 0; j < s2.total(); j++)
			{
				t = distanceSquared(p(s1, i), p(s2, j) );
				if (t > d)
				{
					d = t;
					i0 = i;
					j0 = j;
				}
			}
		return new int[]{i0, j0};
	}
	
//	private static int[] farestPoints(CvSeq s){
//		int i0 = 0, j0 = 0;
//		double d = 0, t;
//		for(int i = 0; i < s.total()-1; i++)
//			for(int j = i+1; j < s.total(); j++)
//			{
//				t = distanceSquared(p(s, i), p(s, j));
//				if (t > d)
//				{
//					d = t;
//					i0 = i;
//					j0 = j;
//				}
//			}
//		return new int[]{i0, j0};
//	}
	
	public static double distance(CvPoint p1, CvPoint p2){
		return Math.sqrt((p1.x() - p2.x())*(p1.x() - p2.x()) + (p1.y() - p2.y())*(p1.y() - p2.y()));
	}
	
	private static double distanceSquared(CvPoint p1, CvPoint p2){
		return (p1.x() - p2.x())*(p1.x() - p2.x()) + (p1.y() - p2.y())*(p1.y() - p2.y());
	}
	
	private static CvPoint p(CvSeq s, int i)
	{
		return new CvPoint(cvGetSeqElem(s, i));
	}
	
	private static void sum(CvPoint in1, CvPoint in2, CvPoint out){
		out.x(in1.x() + in2.x());
		out.y(in1.y() + in2.y());
	}
	
	private static void sub(CvPoint in1, CvPoint in2, CvPoint out){
		out.x(in1.x() - in2.x());
		out.y(in1.y() - in2.y());
	}
	
//	private static void mult(CvPoint in1, float val, CvPoint out){
//		out.x(Math.round(in1.x()*val));
//		out.y(Math.round(in1.y()*val));
//	}
	
	private static void mult(CvPoint p, float val){
		p.x(Math.round(p.x()*val));
		p.y(Math.round(p.y()*val));
	}
	
	private static int cross(CvPoint p1, CvPoint p2){
		return p1.x()*p2.y() - p1.y()*p2.x();
	}
	
	private static int dot(CvPoint p1, CvPoint p2){
		return p1.x()*p2.x() + p1.y()*p2.y();
	}
	
	
	private static int farestAlongSide(CvSeq s1, CvPoint start, CvPoint vec){
		int i0 = 0;
		double d = 0, t;
		CvPoint tvec = new CvPoint();
		for(int i = 0; i < s1.total(); i++)
			{
				sub(p(s1,i), start, tvec);
				t = dot(vec, tvec) - Math.abs(cross(vec, tvec));
				if (t > d)
				{
					d = t;
					i0 = i;
				}
			}
		return i0;
	}
	
	private static final double range = 10e3;
	public static double[] lineEq(CvPoint p1, CvPoint p2){
		double dx = p2.x() - p1.x();
		double dy = p2.y() - p1.y();
//		System.out.println(dx + " --- " + dy);
		
		return dx == 0 || Math.abs(dx)*range < Math.abs(dy) ?
				new double[] { Double.POSITIVE_INFINITY, (double)(p1.x()+p2.x()) / 2}
				: new double[] { dy/dx, p1.y() - dy/dx*p1.x()};
	}
	
	public static void intersect(double[] l1, double[] l2, CvPoint out){
		int x, y;
		if (Double.isInfinite(l1[0]))
		{
			x = (int)Math.round(l1[1]);
			y = (int)Math.round(l2[0]*x + l2[1]);
		} else if (Double.isInfinite(l2[0])){
			x = (int)Math.round(l2[1]);
			y = (int)Math.round(l1[0]*x + l1[1]);
		} else {
			x = (int)Math.round((l2[1]-l1[1])/(l1[0]-l2[0]));
			y = (int)Math.round(l1[0]*x + l1[1]);
		}
		
		out.x(x);
		out.y(y);
	}
	
//	private static int countContours(CvSeq contours){
//		CvSeq c = contours;
//		int n = 0;
//		while(c != null){
//			n++;
//			c = c.h_next();
//		}
//		return n;
//	}
	
	public static double[] lineEq(CvArr twoPoints){
		CvPoint pt1 = new CvPoint();
		CvPoint pt2 = new CvPoint();
		
		CvScalar p = new CvScalar(2);
		p = cvGet1D(twoPoints, 0);
		pt1.x((int)Math.round(p.get(0)));
		pt1.y((int)Math.round(p.get(1)));
		p = cvGet1D(twoPoints, 1);
		pt2.x((int)Math.round(p.get(0)));
		pt2.y((int)Math.round(p.get(1)));
		
		
		return lineEq(pt1, pt2);
	}
	
	public static <K, V extends Comparable<? super V>> ArrayList<K> 
    	sortByValue( Map<K, V> map )
	{
		ArrayList<K> result = new ArrayList<K>();
	    Stream <Entry<K,V>> st = map.entrySet().stream();
	
	    st.sorted(Comparator.comparing(e -> e.getValue()))
	          .forEach(e ->result.add(e.getKey()));
	
	    return result;
	}
	
}



