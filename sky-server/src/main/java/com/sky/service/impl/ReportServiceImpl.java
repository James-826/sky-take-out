package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while(!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>();
        for(LocalDate date:dateList) {

            Map map=new HashMap();
            map.put("begin", LocalDateTime.of(date, LocalTime.MIN));
            map.put("end",LocalDateTime.of(date, LocalTime.MAX));
            map.put("status", Orders.COMPLETED);
            Double turnover =orderMapper.sumByMap(map);
            turnover=turnover==null?0:turnover;
            turnoverList.add(turnover);
        }

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,",") )
                .build();
    }

    /**
     * 用户数据统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        for(LocalDate date:dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer newUser=getUserCount(beginTime,endTime);
            Integer totalUser=getUserCount(null,endTime);

            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(newUserList,",") )
                .totalUserList(StringUtils.join(totalUserList,",") )
                .build();
    }

    /**
     * 订单数据统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> orderCountList  = new ArrayList<>();
        List<Integer> validOrderCountList  = new ArrayList<>();
        for(LocalDate date:dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Integer orderCount=getOrderCount(beginTime,endTime,null);
            Integer validOrderCount=getOrderCount(beginTime,endTime,Orders.COMPLETED);
            orderCount=orderCount==null?0:orderCount;
            validOrderCount=validOrderCount==null?0:validOrderCount;

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);

        }
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        Double orderCompletionRate=0.0;
        if(totalOrderCount!=0) {
            orderCompletionRate = (double) validOrderCount / totalOrderCount;
        }
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .orderCompletionRate(orderCompletionRate)
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .build();
    }

    /**
     * 销量排名top10
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop = orderMapper.getSalesTop(beginTime, endTime);
        SalesTop10ReportVO salesTop10ReportVO=new SalesTop10ReportVO();
        List<String> names=salesTop.stream().map(x->
        {String name = x.getName();return name;
        }).collect(Collectors.toList());
        List<Integer> numbers=salesTop.stream().map(x->
        {Integer number = x.getNumber();return number;
        }).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");
        String numberList = StringUtils.join(numbers, ",");
        salesTop10ReportVO.setNameList(nameList);
        salesTop10ReportVO.setNumberList(numberList);

        return salesTop10ReportVO;
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin=LocalDate.now().minusDays(30);
        LocalDate end=LocalDate.now().minusDays(1);
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = excel.getSheet("Sheet1");
            sheet.getRow(1).getCell(1).setCellValue(begin+"至"+end);
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());
            for(int i=0;i<30;i++)
            {
                LocalDate date=begin.plusDays(i);
                workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row=sheet.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            out.flush();
            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {
        Map map=new HashMap();
        map.put("begin",beginTime);
        map.put("end",endTime);
        return userMapper.countByMap(map);
    }
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime,Integer status) {
        Map map=new HashMap();
        map.put("begin",beginTime);
        map.put("end",endTime);
        map.put("status",status);
        return orderMapper.countByMap(map);
    }
}
