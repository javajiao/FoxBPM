/**
 * Copyright 1996-2014 FoxBPM ORG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * @author MAENLIANG
 */
package org.foxbpm.engine.impl.diagramview.svg.builder;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.foxbpm.engine.exception.FoxBPMException;
import org.foxbpm.engine.impl.diagramview.svg.Point;
import org.foxbpm.engine.impl.diagramview.svg.PointUtils;
import org.foxbpm.engine.impl.diagramview.svg.SVGUtils;
import org.foxbpm.engine.impl.diagramview.svg.vo.MarkerVO;
import org.foxbpm.engine.impl.diagramview.svg.vo.PathVO;
import org.foxbpm.engine.impl.diagramview.svg.vo.SvgVO;

/**
 * 默认线条式样构造
 * 
 * @author MAENLIANG
 * @date 2014-06-10
 */
public class ConnectorSVGBuilder extends AbstractSVGBuilder {
	private static final String FILL_DEFAULT = "none";
	private static final String MOVETO_FLAG = "M";
	private static final String LINETO_FLAG = "L";
	private static final String PATHCIRCLE_FLAG = "C";
	
	private static final String D_SPACE = " ";
	private PathVO pathVo;
	private MarkerVO endMarker;
	public ConnectorSVGBuilder(SvgVO svgVo) {
		super(svgVo);
		this.textVO = svgVo.getgVo().getgVoList().get(0).getTextVo();
		this.pathVo = SVGUtils.getSequenceVOFromSvgVO(svgVo);
		endMarker =  svgVo.getgVo().getDefsVo().getMarkerVOList().get(1);
		if (pathVo == null) {
			throw new FoxBPMException("线条元素初始化工厂时候报错，pathVo对象为空");
		}
		
	}
	
	/**
	 * 设置线条的拐点信息,拐角划圆形
	 */
	public void setWayPoints(List<Point> pointList) {
		if (pointList == null || pointList.size() == 0) {
			return;
		}
		StringBuffer pathBuffer = new StringBuffer();
		pathBuffer.append(MOVETO_FLAG);
		float firstPointX = pointList.get(0).getX();
		float firstPointY = pointList.get(0).getY();
		pathBuffer.append(String.valueOf(firstPointX));
		pathBuffer.append(D_SPACE).append(String.valueOf(firstPointY));
		int size = pointList.size();
		Point point = null;
		Point startPoint = null;
		Point center = null;
		Point end = null;
		Point[] bezalPoints = null;
		for (int i = 1; i < size; i++) {
			point = pointList.get(i);
			if (size < 3) {
				// 小于三个断点的，直接画直线
				pathBuffer.append(LINETO_FLAG).append(String.valueOf(point.getX())).append(D_SPACE).append(String.valueOf(point.getY()));
			} else {
				if (i != size - 1) {
					// 根据三个端点计算 三次贝塞尔曲线的起始点 控制点 终点
					startPoint = pointList.get(i - 1);
					center = point;
					end = pointList.get(i + 1);
					// 以当前坐标为中心点，数组大小是4，结构依次是 [起始点] [控制点A] [控制点B] [终点]
					bezalPoints = PointUtils.caclBeralPoints(startPoint, center, end);
					if (bezalPoints[0].getX() != 0.0f && bezalPoints[0].getY() != 0.0f) {
						pathBuffer.append(LINETO_FLAG).append(bezalPoints[0].getX()).append(D_SPACE).append(bezalPoints[0].getY()).append(D_SPACE);
					}
					
					if (bezalPoints[1].getX() != 0.0f && bezalPoints[1].getY() != 0.0f
					        && bezalPoints[2].getX() != 0.0f && bezalPoints[2].getY() != 0.0f
					        && bezalPoints[3].getX() != 0.0f && bezalPoints[3].getY() != 0.0f) {
						pathBuffer.append(PATHCIRCLE_FLAG).append(bezalPoints[1].getX()).append(D_SPACE).append(bezalPoints[1].getY()).append(D_SPACE).append(bezalPoints[2].getX()).append(D_SPACE).append(bezalPoints[2].getY()).append(D_SPACE).append(bezalPoints[3].getX()).append(D_SPACE).append(bezalPoints[3].getY()).append(D_SPACE);
					}
					
					if (i == size - 2) {
						pathBuffer.append(LINETO_FLAG).append(end.getX()).append(D_SPACE).append(end.getY());
					}
				}
			}
		}
		this.pathVo.setD(pathBuffer.toString());
	}
	 
	public void setStroke(String stroke) {
		if (StringUtils.isBlank(stroke)) {
			this.pathVo.setStroke(STROKE_DEFAULT);
			return;
		}
		this.pathVo.setStroke(COLOR_FLAG + stroke);
	}
	
	 
	public void setStrokeWidth(float strokeWidth) {
		this.pathVo.setStrokeWidth(strokeWidth);
		
	}
	
	 
	public void setFill(String fill) {
		if (StringUtils.isBlank(fill)) {
			this.pathVo.setFill(FILL_DEFAULT);
			return;
		}
		this.pathVo.setFill(COLOR_FLAG + fill);
	}
	
	 
	public void setID(String id) {
		this.pathVo.setId(id);
		String markerId = id+"Marker";
		this.endMarker.setId(markerId);
		this.endMarker.getPathVOList().get(0).setId(markerId+"Path");
		pathVo.setMarkerEnd("url(#"+markerId+")");
	}
	
	 
	public void setName(String name) {
		this.pathVo.setName(name);
		
	}
	
	 
	public void setStyle(String style) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setTypeStroke(String stroke) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setTypeStrokeWidth(float strokeWidth) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setTypeFill(String fill) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setTypeStyle(String style) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setWidth(float width) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setHeight(float height) {
		// TODO Auto-generated method stub
		
	}
	
	 
	public void setXAndY(float x, float y) {
	}
}
