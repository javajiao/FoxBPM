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
 * @author demornain
 */
package org.foxbpm.calendar.mybatis.cmd;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.foxbpm.calendar.mybatis.entity.CalendarPartEntity;
import org.foxbpm.calendar.mybatis.entity.CalendarRuleEntity;
import org.foxbpm.calendar.mybatis.entity.CalendarTypeEntity;
import org.foxbpm.calendar.service.WorkCalendarService;
import org.foxbpm.calendar.utils.DateCalUtils;
import org.foxbpm.engine.ProcessEngineManagement;
import org.foxbpm.engine.exception.FoxBPMIllegalArgumentException;
import org.foxbpm.engine.impl.interceptor.Command;
import org.foxbpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetWorkCalendarEndTimeCmd implements Command<Date> {
	
	private static Logger log = LoggerFactory.getLogger(GetWorkCalendarEndTimeCmd.class);
	//工作状态
	public static final int WORKSTATUS = 0;
	//假期状态
	public static final int FREESTATUS = 1;
	public static final long HOURTIME = 1000L * 60 * 60;
	private Date begin;
	private double hours;
	private WorkCalendarService workCalendarService;
	private SimpleDateFormat timeFormat;
	private int year = 0;
	private int month = 0;
	private int day = 0;
	private CalendarTypeEntity calendarTypeEntity;
	private boolean isAddDay = false;
	private String ruleId;

	public GetWorkCalendarEndTimeCmd(Date begin,double hours ,String ruleId) {
		this.begin = begin;
		this.hours = hours;
		this.workCalendarService = ProcessEngineManagement.getDefaultProcessEngine().getService(WorkCalendarService.class);
		this.timeFormat = new SimpleDateFormat("hh:mm");
		this.ruleId = ruleId;
	}
	
	 
	public Date execute(CommandContext commandContext) {
		//拿到参数中的时间
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(begin);
		year = calendar.get(Calendar.YEAR);
		month = calendar.get(Calendar.MONTH) + 1;
		day = calendar.get(Calendar.DATE);
		
		//拿到对应的工作日历方案
		calendarTypeEntity = getCalendarTypeById(ruleId);
		
		//初始化日历类型，找到里面所有的工作时间
		initCalendarType(calendarTypeEntity);
		
		CalendarRuleEntity calendarRuleEntity = null;
		//从日历类型拿到对应的工作时间
		for (int k=0; k<calendarTypeEntity.getCalendarRuleEntities().size();k++) {
			CalendarRuleEntity calRuleEntity = calendarTypeEntity.getCalendarRuleEntities().get(k);
			//先判断在不在假期时间里
			if(calRuleEntity.getStatus()==FREESTATUS && calRuleEntity.getWorkdate()!=null && calRuleEntity.getYear()==year && DateUtils.isSameDay(calRuleEntity.getWorkdate(), begin)) {
				//如果这天没有设置时间段则跳过这一整天
				if(calRuleEntity.getCalendarPartEntities().size()==0) {
					begin = DateUtils.addDays(begin, 1);
					calendar.setTime(begin);
					calendar.set(Calendar.HOUR, 0);
					calendar.set(Calendar.MINUTE, 0);
					calendar.set(Calendar.SECOND, 0);
					begin = calendar.getTime();
					year = calendar.get(Calendar.YEAR);
					month = calendar.get(Calendar.MONTH) + 1;
					day = calendar.get(Calendar.DATE);
					calendar.get(Calendar.HOUR);
					calendar.get(Calendar.MINUTE);
					calendar.get(Calendar.SECOND);
				}
				//如果有设置时间段 则算出这天的工作时间去除假期时间的时间段 然后再计算
				else {
					calendarRuleEntity = getCalendarRuleEntityWithHoliday(calRuleEntity);
				}
			}
			//判断在不在工作时间里
			if(calRuleEntity.getWeek()!=0 && calRuleEntity.getYear()==year && calRuleEntity.getWeek()==DateCalUtils.dayForWeek(begin)) {
				calendarRuleEntity = calRuleEntity;
			}
			//如果不在工作时间内则继续循环找
			if(calendarRuleEntity == null) {
				continue;
			}
			//当找到规则时开始算时间
			else {
				Calendar endCalendar = Calendar.getInstance();
				Date endDate = CalculateEndTime(calendarRuleEntity);
				endCalendar.setTime(endDate);
				log.debug("最终的计算结果为：" + endCalendar.getTime());
				return endDate;
			}
		}
		
		log.debug("所给时间不在工作时间内，计算出错");
		return null;
	}
	
	/**
	 * 计算假期后的时间段
	 * @param calRuleEntity
	 * @return
	 */
	private CalendarRuleEntity getCalendarRuleEntityWithHoliday(CalendarRuleEntity calRuleEntity) {
		List<CalendarPartEntity> rightPartEntities = new ArrayList<CalendarPartEntity>();
		if(calRuleEntity.getYear()==year && DateCalUtils.dayForWeek(calRuleEntity.getWorkdate())==DateCalUtils.dayForWeek(begin)) {
			for (CalendarRuleEntity workRuleEntity : calendarTypeEntity.getCalendarRuleEntities()) {
				//找到同一天的工作时间
				if(workRuleEntity.getWeek() == DateCalUtils.dayForWeek(calRuleEntity.getWorkdate())) {
					//工作时间
					List<CalendarPartEntity> workPartEntities = workRuleEntity.getCalendarPartEntities();
					//休息时间
					List<CalendarPartEntity> freePartEntities = calRuleEntity.getCalendarPartEntities();
					
					int size = workPartEntities.size()<freePartEntities.size()?workPartEntities.size():freePartEntities.size();
					
					for (int i = 0; i < size; i++) {
						String workStart = workPartEntities.get(i).getStarttime();
						String workEnd = workPartEntities.get(i).getEndtime();
						
						String freeStart = freePartEntities.get(i).getStarttime();
						String freeEnd = freePartEntities.get(i).getEndtime();

						long workStartDate = getCalculateTime(workStart, workPartEntities.get(i).getAmorpm());
						long workEndDate = getCalculateTime(workEnd, workPartEntities.get(i).getAmorpm());
						
						long freeStartDate = getCalculateTime(freeStart, workPartEntities.get(i).getAmorpm());
						long freeEndDate = getCalculateTime(freeEnd, workPartEntities.get(i).getAmorpm());
						
						//上下午时间段相同再计算
						if(workPartEntities.get(i).getAmorpm()==workPartEntities.get(i).getAmorpm()) {
							calculateWorkTimeWithHoliday(workStartDate, workEndDate, freeStartDate, freeEndDate, workPartEntities.get(i), i, rightPartEntities);
						}
					}
				}
			}
		}
		for (CalendarPartEntity calendarPartEntity : rightPartEntities) {
			log.debug("和假期修改计算后时间段为:" + calendarPartEntity.getStarttime() + "---" + calendarPartEntity.getEndtime());
		}
		calRuleEntity.setCalendarPartEntities(rightPartEntities);
		return calRuleEntity;
	}

/**
 * 计算结束时间
 * @param calendarRuleEntity
 * @return
 */
	private Date CalculateEndTime(CalendarRuleEntity calendarRuleEntity) {
		//如果这天没有规则则再加一天计算
		if(getCalendarRuleByDate(begin)==null) {
			day+=1;
			begin = DateUtils.addDays(begin, 1);
			CalculateEndTime(calendarRuleEntity);
		}
		
		Date endDate = null;
		
		for (CalendarRuleEntity caRuleEntity : calendarTypeEntity.getCalendarRuleEntities()) {
			//先判断在不在假期时间里
			if(caRuleEntity.getStatus()==FREESTATUS && caRuleEntity.getWorkdate()!=null && caRuleEntity.getYear()==year && DateUtils.isSameDay(caRuleEntity.getWorkdate(), begin)) {
				//如果这天没有设置时间段则跳过这一整天
				if(caRuleEntity.getCalendarPartEntities().size()==0) {
					begin = DateUtils.addDays(begin, 1);
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(begin);
					calendar.set(Calendar.HOUR, 0);
					calendar.set(Calendar.MINUTE, 0);
					calendar.set(Calendar.SECOND, 0);
					begin = calendar.getTime();
					year = calendar.get(Calendar.YEAR);
					month = calendar.get(Calendar.MONTH) + 1;
					day = calendar.get(Calendar.DATE);
					calendar.get(Calendar.HOUR);
					calendar.get(Calendar.MINUTE);
					calendar.get(Calendar.SECOND);
				}
				//如果有设置时间段 则算出这天的工作时间去除假期时间的时间段 然后再计算
				else {
					calendarRuleEntity = getCalendarRuleEntityWithHoliday(caRuleEntity);
				}
			}
		}
		
		//如果本天是假期就加一天继续
		if(calendarRuleEntity.getStatus() == FREESTATUS && calendarRuleEntity.getCalendarPartEntities().size()==0) {
			day+=1;
			calendarRuleEntity = getCalendarRuleByDate(DateUtils.addDays(begin, 1));
			Calendar calendar = Calendar.getInstance();
			
			if(calendarRuleEntity.getCalendarPartEntities().size()>0) {
				calendar.setTimeInMillis(getCalculateTime(calendarRuleEntity.getCalendarPartEntities().get(0).getStarttime(), calendarRuleEntity.getCalendarPartEntities().get(0).getAmorpm()));
				begin = calendar.getTime();
			}
			endDate = CalculateEndTime(calendarRuleEntity);
		}
		for (int j=0;j<calendarRuleEntity.getCalendarPartEntities().size();j++) {
			CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(j);
			//先匹配上午下午 先默认里面只有一个上午一个下午  //TODO 暂时还没试，应该是支持的
			if(calendarPartEntity.getAmorpm() == DateCalUtils.dayForAMorPM(begin)) {
				Calendar formatCalendar = Calendar.getInstance();

				//时间段开始的毫秒数
				long startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
				formatCalendar.setTimeInMillis(startTime);
				log.debug("开始时间段为" + formatCalendar.getTime());

				//时间段结束的毫秒数
				long endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());
				formatCalendar.setTimeInMillis(endTime);
				log.debug("结束时间段为" + formatCalendar.getTime());
				
				//传过来时间的毫秒数
				long beginTime = begin.getTime();
				formatCalendar.setTime(begin);
				log.debug("参数开始时间段为" + formatCalendar.getTime());
				
				//传过来的时间加上小时数的毫秒数
				long beginEndTime = (long) (hours * HOURTIME + beginTime);
				formatCalendar.setTimeInMillis(beginEndTime);
				log.debug("预计结束时间段为" + formatCalendar.getTime());
				
				log.debug("剩余时间为" +  hours + "小时");
				
				endDate = CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, j);
				if(endDate==null) {
					log.debug("计算出错");
					break;
				}
				
				return endDate;
				}
			}
		return endDate;
	}
	
	/**
	 * 具体分情况计算时间 递归
	 * @param startTime
	 * @param endTime
	 * @param beginTime
	 * @param beginEndTime
	 * @param calendarRuleEntity
	 * @param i
	 * @return
	 */
	private Date CalculateTime(long startTime, long endTime, long beginTime, long beginEndTime, CalendarRuleEntity calendarRuleEntity, int i) {
		Calendar endCalendar = Calendar.getInstance();
		//1、不在时间段内
		if((beginTime<startTime && beginEndTime<startTime)) {
			//还没到工作开始时间，找本天工作时间开始算
			CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(i);
			startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
			endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());

			beginTime = startTime;
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(beginTime);
			begin = calendar.getTime();
			beginEndTime = (long) (beginTime + hours * HOURTIME);
			
			//再次计算
			return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i);
		}
		//如果是已经是超过时间段结束时间的，找下一个时间段
		else if((beginTime>endTime && beginEndTime>endTime)) {
			//找下一个时间段
			if(i+1<calendarRuleEntity.getCalendarPartEntities().size()) {
				CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(i+1);
				startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
				endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());

				beginTime = startTime;
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(beginTime);
				begin = calendar.getTime();
				beginEndTime = (long) (beginTime + hours * HOURTIME);
				
				//再次计算
				return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i + 1);
			}
			//如果已经加过天数，则开始相减
			if(isAddDay) {
				CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(0);
				startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
				endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());
				beginTime = startTime;
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(beginTime);
				begin = calendar.getTime();
				beginEndTime = (long) (beginTime + hours * HOURTIME);
				
				//再次计算
				isAddDay = false;
				return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i);
			}
			
			//如果这天全部没有 需要往后推工作时间
			else {
				CalendarRuleEntity calendarRuleEntity2 = getCalendarRuleByDate(DateUtils.addDays(begin, 1));
				if(calendarRuleEntity2.getStatus()==FREESTATUS) {
					day +=1;
				} else {
					day +=1;
					isAddDay = true;
					startTime = getCalculateTime(calendarRuleEntity2.getCalendarPartEntities().get(0).getStarttime(), calendarRuleEntity2.getCalendarPartEntities().get(0).getAmorpm());
					endTime = getCalculateTime(calendarRuleEntity2.getCalendarPartEntities().get(0).getEndtime(), calendarRuleEntity2.getCalendarPartEntities().get(0).getAmorpm());
					beginTime = startTime;
					Calendar calendar = Calendar.getInstance();
					calendar.setTimeInMillis(beginTime);
					begin = calendar.getTime();
					beginEndTime = (long) (beginTime + hours * HOURTIME);
				}
				return CalculateEndTime(calendarRuleEntity2);
			}
		}
		
		//2、左边超出开始时间段 ，结束时间在时间段内
		else if(beginTime<startTime && beginEndTime>startTime && beginEndTime<=endTime) {
			//用开始的时间段开始计算，
			//如果右边没超出，则是整段时间加上
			if((endTime-startTime)-hours * HOURTIME>0) {
				endCalendar.setTimeInMillis((long) (startTime + hours * HOURTIME));
				return endCalendar.getTime();
			}
			//如果右边超出了，先减去这个时间段的工作时间，再找下一个时间段
			else {
				if(i+1<calendarRuleEntity.getCalendarPartEntities().size()) {
					CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(i+1);
					beginEndTime = (long) (hours * HOURTIME - (endTime - startTime));
					hours = hours - ((double)(endTime-startTime)/(HOURTIME));
					startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
					endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());
					beginTime = startTime;
					Calendar calendar = Calendar.getInstance();
					calendar.setTimeInMillis(beginTime);
					begin = calendar.getTime();
					beginEndTime += startTime;
					
					//再次计算
					return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i + 1);
				}
			}
		}
		
		//3、正好两边都在时间段内
		else if(startTime<=beginTime && beginEndTime>startTime && beginEndTime<=endTime) {
			//直接开始加时间
			endCalendar.setTimeInMillis(beginEndTime);
		}
		
		//4、右边超出时间段
		else if(startTime<=beginTime && beginEndTime>endTime) {
			CalendarPartEntity calendarPartEntity = null;
			if(i+1<calendarRuleEntity.getCalendarPartEntities().size()) {
				calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(i+1);
				//如果开始时间刚好，则就这个时间段开始算 可以整块减掉工作时间
				if(startTime == beginTime) {
					beginEndTime = (long) (hours * HOURTIME - (endTime - startTime));
					hours = hours - ((double)(endTime-startTime)/(HOURTIME));
				}
				//否则只能拿工作结束时间减去开始时间
				else {
					beginEndTime = (long) (hours * HOURTIME - (endTime - beginTime));
					hours = hours - ((double)(endTime - beginTime)/(HOURTIME));
				}
				
				startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
				endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());
				beginTime = startTime;
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(beginTime);
				begin = calendar.getTime();
				beginEndTime += startTime;
				
				//再次计算
				return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i + 1);
			}
				
			//如果本天的时间段都不够用了，则加一天再算
			else{
				CalendarRuleEntity calendarRuleEntity2 = getCalendarRuleByDate(DateUtils.addDays(begin, 1));
				if(calendarRuleEntity2.getStatus()==FREESTATUS || isHoliday(begin)) {
					day +=1;
					//如果开始时间刚好，则就这个时间段开始算 可以整块减掉工作时间
					if(startTime == beginTime) {
						beginEndTime = (long) (hours * HOURTIME - (endTime - startTime));
						hours = hours - ((double)(endTime-startTime)/(HOURTIME));
					}
					//否则只能拿工作结束时间减去开始时间
					else {
						beginEndTime = (long) (hours * HOURTIME - (endTime - beginTime));
						hours = hours - ((double)(endTime - beginTime)/(HOURTIME));
					}
				}else{
					day +=1;
					//如果开始时间刚好，则就这个时间段开始算 可以整块减掉工作时间
					if(startTime == beginTime) {
						beginEndTime = (long) (hours * HOURTIME - (endTime - startTime));
						hours = hours - ((double)(endTime-startTime)/(HOURTIME));
					}
					//否则只能拿工作结束时间减去开始时间
					else {
						beginEndTime = (long) (hours * HOURTIME - (endTime - beginTime));
						hours = hours - ((double)(endTime - beginTime)/(HOURTIME));
					}
					startTime = getCalculateTime(calendarRuleEntity2.getCalendarPartEntities().get(0).getStarttime(), calendarRuleEntity2.getCalendarPartEntities().get(0).getAmorpm());
					endTime = getCalculateTime(calendarRuleEntity2.getCalendarPartEntities().get(0).getEndtime(), calendarRuleEntity2.getCalendarPartEntities().get(0).getAmorpm());
					beginTime = startTime;
					Calendar calendar = Calendar.getInstance();
					calendar.setTimeInMillis(beginTime);
					begin = calendar.getTime();
					beginEndTime += startTime;
				}
				
				return CalculateEndTime(calendarRuleEntity2);
			}
		}
		
		//5、时间覆盖了整段的工作时间（两头都超出）
		else if(beginTime<startTime && beginEndTime>endTime) {
			//还没到工作开始时间，找本天工作时间段开始算
			CalendarPartEntity calendarPartEntity = calendarRuleEntity.getCalendarPartEntities().get(i);
			startTime = getCalculateTime(calendarPartEntity.getStarttime(), calendarPartEntity.getAmorpm());
			endTime = getCalculateTime(calendarPartEntity.getEndtime(), calendarPartEntity.getAmorpm());
			beginTime = startTime;
			beginEndTime = (long) (beginTime + (hours * HOURTIME));
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(beginTime);
			begin = calendar.getTime();
			
			//再次计算
			return CalculateTime(startTime, endTime, beginTime, beginEndTime, calendarRuleEntity, i);
		}
		
		return endCalendar.getTime();
	}

	/**
	 * 判断日期在不在假期里面
	 * @param begin2
	 * @return
	 */
	private boolean isHoliday(Date begin2) {
		boolean isHoliday = false;
		for (CalendarRuleEntity calendarRuleEntity : calendarTypeEntity.getCalendarRuleEntities()) {
			if(calendarRuleEntity.getStatus()==FREESTATUS && calendarRuleEntity.getWorkdate()!=null && calendarRuleEntity.getYear()==year && DateUtils.isSameDay(calendarRuleEntity.getWorkdate(), begin) && calendarRuleEntity.getCalendarPartEntities().size()==0) {
				isHoliday = true;
			}
		}
		return isHoliday;
	}

	/**
	 * 初始化日历类型
	 * @param calendarTypeEntity
	 */
	private void initCalendarType(CalendarTypeEntity calendarTypeEntity) {
		//找到所有类型ID为当前日历类型的日历规则
		List<CalendarRuleEntity> calendarRuleEntities = workCalendarService.getCalendarRulesByTypeId(calendarTypeEntity.getId());
		
		for (CalendarRuleEntity calendarRuleEntity : calendarRuleEntities) {
			//找到所有规则ID为当前日历规则的日历时间
			List<CalendarPartEntity> calendarPartEntities = workCalendarService.getCalendarPartsByRuleId(calendarRuleEntity.getId());
			
			//给时间段排序
			Collections.sort(calendarPartEntities, new Comparator<CalendarPartEntity>() {

				 
				public int compare(CalendarPartEntity o1, CalendarPartEntity o2) {
					SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm");
					long o1s = 0;
					long o2s = 0;
					Calendar calendar = Calendar.getInstance();
					Date o1d = null;
					try {
						o1d = simpleDateFormat.parse(o1.getStarttime());
					} catch (ParseException e) {
						throw new FoxBPMIllegalArgumentException("时间格式错误！期望格式：HH:mm,实际格式："+o1.getStarttime());
					}
					calendar.setTime(o1d);
					//"12点和0点特殊对待上午下午"
					if(o1.getStarttime().equals("12:00")) {
						calendar.set(Calendar.AM_PM, 1);
					}
					else if(o1.getStarttime().equals("00:00")) {
						calendar.set(Calendar.AM_PM, 0);
					}else {
						calendar.set(Calendar.AM_PM, o1.getAmorpm());
					}
					
					o1s = calendar.getTimeInMillis();
					
					Date o2d = null;
					try {
						o2d = simpleDateFormat.parse(o2.getStarttime());
					} catch (ParseException e) {
						throw new FoxBPMIllegalArgumentException("时间格式错误！期望格式：HH:mm,实际格式："+o2.getStarttime());
					}
					calendar.setTime(o2d);
					//"12点和0点特殊对待上午下午"
					if(o2.getStarttime().equals("12:00")) {
						calendar.set(Calendar.AM_PM, 1);
					}
					else if(o2.getStarttime().equals("00:00")) {
						calendar.set(Calendar.AM_PM, 0);
					}else {
						calendar.set(Calendar.AM_PM, o2.getAmorpm());
					}
					
					o2s = calendar.getTimeInMillis();
					calendar.getTime();
					
					return o1s<o2s==true?0:1;
				}
			});
			
			calendarRuleEntity.setCalendarPartEntities(calendarPartEntities);
		}
		
		calendarTypeEntity.setCalendarRuleEntities(calendarRuleEntities);
	}

	/**
	 * 根据userId找到对应的日历类型
	 * @param userId2
	 * @return
	 */
	private CalendarTypeEntity getCalendarTypeById(String userId2) {
		return workCalendarService.getCalendarTypeById(userId2);
	}

	/**
	 * 获取毫秒时间用于计算
	 * @param date
	 * @param amorpm
	 * @return
	 * @throws ParseException
	 */
	private long getCalculateTime(String date, int amorpm){
		Date startDate = null;
		try{
			startDate = timeFormat.parse(date);
		}catch(ParseException ex){
			throw new FoxBPMIllegalArgumentException("时间格式错误！期望格式：HH:mm,实际格式："+date);
		}
		
		Calendar formatCalendar = Calendar.getInstance();
		formatCalendar.setTime(startDate);
		formatCalendar.set(Calendar.YEAR, year);
		formatCalendar.set(Calendar.MONTH, month-1);
		formatCalendar.set(Calendar.DATE, day);
		//"12点和0点特殊对待上午下午"
		if(date.equals("12:00")) {
			formatCalendar.set(Calendar.AM_PM, 1);
		}
		else if(date.equals("00:00")) {
			formatCalendar.set(Calendar.AM_PM, 0);
		}else {
			formatCalendar.set(Calendar.AM_PM, amorpm);
		}
		
		//时间段开始的毫秒数
		long startTime = formatCalendar.getTimeInMillis();
		
		return startTime;
	}
	
	private void calculateWorkTimeWithHoliday(long workStartDate, long workEndDate, long freeStartDate, long freeEndDate, CalendarPartEntity workPart, int i, List<CalendarPartEntity> rightPartEntities) {
		Calendar calendar = Calendar.getInstance();
		
		//1、假期不在工作时间段内(基本上用不到。。)
		if(freeStartDate<=workStartDate && freeEndDate<=workStartDate) {
			return;
		}
		//2、两头都在工作时间段内
		else if(freeStartDate>=workStartDate && freeEndDate<=workEndDate) {
			//如果两头时间刚好相等，则没有这段时间了
			if(freeStartDate == workStartDate && freeEndDate== workEndDate) {
				return;
			}else {
				CalendarPartEntity calendarPartEntity = new CalendarPartEntity(java.util.UUID.randomUUID().toString());
				calendarPartEntity.setAmorpm(workPart.getAmorpm());
				calendarPartEntity.setRuleid(workPart.getRuleid());
				calendar.setTimeInMillis(workStartDate);
				calendarPartEntity.setStarttime(timeFormat.format(calendar.getTime()));
				calendar.setTimeInMillis(freeStartDate);
				calendarPartEntity.setEndtime(timeFormat.format(calendar.getTime()));
				rightPartEntities.add(calendarPartEntity);
				
				CalendarPartEntity calendarPartEntitynew = new CalendarPartEntity(java.util.UUID.randomUUID().toString());
				calendarPartEntitynew.setAmorpm(workPart.getAmorpm());
				calendarPartEntitynew.setRuleid(workPart.getRuleid());
				calendar.setTimeInMillis(freeEndDate);
				calendarPartEntitynew.setStarttime(timeFormat.format(calendar.getTime()));
				calendar.setTimeInMillis(workEndDate);
				calendarPartEntitynew.setEndtime(timeFormat.format(calendar.getTime()));
				rightPartEntities.add(calendarPartEntitynew);
			}
		}
		//3、左边超出
		else if(freeStartDate<workStartDate && freeEndDate<=workEndDate) {
			CalendarPartEntity calendarPartEntity = new CalendarPartEntity(java.util.UUID.randomUUID().toString());
			calendarPartEntity.setAmorpm(workPart.getAmorpm());
			calendarPartEntity.setRuleid(workPart.getRuleid());
			calendarPartEntity.setStarttime(timeFormat.format(workStartDate));
			calendarPartEntity.setEndtime(timeFormat.format(freeEndDate));
			rightPartEntities.add(calendarPartEntity);
			
			
		}
		//4、右边超出
		else if(freeStartDate>=workStartDate && freeEndDate>workEndDate) {
			CalendarPartEntity calendarPartEntity = new CalendarPartEntity(java.util.UUID.randomUUID().toString());
			calendarPartEntity.setAmorpm(workPart.getAmorpm());
			calendarPartEntity.setRuleid(workPart.getRuleid());
			calendarPartEntity.setStarttime(timeFormat.format(freeStartDate));
			calendarPartEntity.setEndtime(timeFormat.format(workEndDate));
		}
	}
	
	/**
	 * 根据日期拿到对应的规则
	 * @param date
	 * @return
	 */
	private CalendarRuleEntity getCalendarRuleByDate(Date date) {
		CalendarRuleEntity calendarRuleEntity = null;
		for (CalendarRuleEntity calendarRuleEntity2 : calendarTypeEntity.getCalendarRuleEntities()) {
			if(calendarRuleEntity2.getWeek()==DateCalUtils.dayForWeek(date)) {
				calendarRuleEntity = calendarRuleEntity2;
			}
			if(calendarRuleEntity2.getWorkdate()!=null && DateUtils.isSameDay(calendarRuleEntity2.getWorkdate(), date) && calendarRuleEntity2.getCalendarPartEntities().size()!=0) {
				calendarRuleEntity = calendarRuleEntity2;
			}
		}
		//如果这天没有规则则再加一天
		if(calendarRuleEntity==null) {
			date = DateUtils.addDays(date, 1);
			return getCalendarRuleByDate(date);
		}
		return calendarRuleEntity;
	}
	
//	/**
//	 * 根据传入的规则找到下一天的规则
//	 * @param currentRule
//	 * @return
//	 */
//	private CalendarRuleEntity getNextCalendarRule(CalendarRuleEntity currentRule) {
//		CalendarRuleEntity calendarRule = null;
//		for (CalendarRuleEntity calendarRuleEntity : calendarTypeEntity.getCalendarRuleEntities()) {
//			//1、常规，根据日期找出下面一天的规则
//			if(calendarRuleEntity.getWeek()==(currentRule.getWeek()+1)%7) {
//				calendarRule = calendarRuleEntity;
//			}
//			//如果后一天是节假日或者没有规则的，需要再往后加
//			else if(currentRule.getWeek()==0 || currentRule.getStatus()==FREESTATUS || currentRule.getCalendarPartEntities().size()==0) {
//				
//			}
//		}
//		
//		return calendarRule;
//	}
}
