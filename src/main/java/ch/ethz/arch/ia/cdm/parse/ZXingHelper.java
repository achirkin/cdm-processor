package ch.ethz.arch.ia.cdm.parse;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

public class ZXingHelper {

	public static String readQRCode(BufferedImage img) throws NotFoundException, ChecksumException, FormatException{
//		String charset = "UTF-8"; // or "ISO-8859-1"
		BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
					new BufferedImageLuminanceSource(img)));
		Map<DecodeHintType, Object> hint = new HashMap<DecodeHintType, Object>();
	    hint.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
//	    hint.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);
	    
		Result qrCodeResult = new QRCodeMultiReader().decode(binaryBitmap, hint);
		return qrCodeResult.getText();
  }
}
