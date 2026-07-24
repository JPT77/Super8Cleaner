package de.jpt.super8;
public class VideoInfo{
 public String fileName="";
 public int width,height,totalFrames,currentFrame;
 public double fps;
 public long getPositionMillis(){
  return fps>0?(long)(currentFrame*1000.0/fps):0;
 }
}
