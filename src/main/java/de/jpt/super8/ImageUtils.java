package de.jpt.super8;
import java.awt.image.*;import org.opencv.core.Mat;
public class ImageUtils{
 public static BufferedImage matToBufferedImage(Mat mat){
  if(mat==null||mat.empty()) return null;
  int type=mat.channels()==1?BufferedImage.TYPE_BYTE_GRAY:BufferedImage.TYPE_3BYTE_BGR;
  BufferedImage img=new BufferedImage(mat.cols(),mat.rows(),type);
  byte[] src=new byte[(int)(mat.total()*mat.channels())];
  mat.get(0,0,src);
  byte[] dst=((DataBufferByte)img.getRaster().getDataBuffer()).getData();
  System.arraycopy(src,0,dst,0,src.length);
  return img;
 }
 public static String formatTime(long ms){
  long h=ms/3600000; ms%=3600000;
  long m=ms/60000; ms%=60000;
  long s=ms/1000; ms%=1000;
  return h>0?String.format("%02d:%02d:%02d.%03d",h,m,s,ms):
             String.format("%02d:%02d.%03d",m,s,ms);
 }
}
