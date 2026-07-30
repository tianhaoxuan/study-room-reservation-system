package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.SeatImportResult;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.List;


@Service
public class AdminSeatService {

    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;


    public AdminSeatService(SeatMapper seatMapper, StudyRoomMapper studyRoomMapper, ReservationMapper reservationMapper) {
        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;

    }

    public List<Seat> list(Long roomId) {
        return seatMapper.findByRoomId(roomId);
    }



    @Transactional
    public void add(Seat seat) {
        validateSeat(seat, true);
        if (seatMapper.countByRoomAndSeatNo(seat.getRoomId(), seat.getSeatNo()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "该自习室已存在相同编号的座位");
        }
        if (seat.getStatus() == null) {
            seat.setStatus(BizConstants.SEAT_STATUS_FREE);
        }
        seatMapper.insert(seat);
    }

    @Transactional
    public void update(Seat seat) {
        validateSeat(seat, false);
        if (seatMapper.findById(seat.getId()) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位不存在");
        }
        if (seatMapper.countByRoomAndSeatNoExcludeId(seat.getRoomId(), seat.getSeatNo(), seat.getId()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "该自习室已存在相同编号的座位");
        }
        if (seat.getStatus() == null) {
            seat.setStatus(BizConstants.SEAT_STATUS_FREE);
        }
        seatMapper.update(seat);
    }

    @Transactional
    public void delete(Long id) {
        if (seatMapper.findById(id) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位不存在");
        }
        seatMapper.deleteById(id);
    }

    @Transactional
    public SeatImportResult importSeats(MultipartFile file, Long roomId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "Excel文件不能为空");
        }
        if (studyRoomMapper.findById(roomId) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室不存在");
        }
        int successCount = 0;
        int failCount = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String seatNo = getCellStringValue(row.getCell(0)).trim();
                if (!StringUtils.hasText(seatNo) || seatMapper.countByRoomAndSeatNo(roomId, seatNo) > 0) {
                    failCount++;
                    continue;
                }
                Seat seat = new Seat();
                seat.setRoomId(roomId);
                seat.setSeatNo(seatNo);
                seat.setX((int) getCellNumericValue(row.getCell(1)));
                seat.setY((int) getCellNumericValue(row.getCell(2)));
                seat.setHasPower(toFlag(getCellStringValue(row.getCell(3))));
                seat.setNearWindow(toFlag(getCellStringValue(row.getCell(4))));
                seat.setStatus(BizConstants.SEAT_STATUS_FREE);
                seatMapper.insert(seat);
                successCount++;
            }
        } catch (Exception e) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "Excel文件解析失败：" + e.getMessage());
        }
        return new SeatImportResult(successCount, failCount);
    }

    private void validateSeat(Seat seat, boolean add) {
        if (seat == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位参数不能为空");
        }
        if (!add && seat.getId() == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位ID不能为空");
        }
        if (seat.getRoomId() == null || studyRoomMapper.findById(seat.getRoomId()) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室不存在");
        }
        if (!StringUtils.hasText(seat.getSeatNo())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位编号不能为空");
        }
    }

    private int toFlag(String value) {
        return "是".equals(value) || "1".equals(value) || "true".equalsIgnoreCase(value) ? 1 : 0;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double value = cell.getNumericCellValue();
                return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    private double getCellNumericValue(Cell cell) {
        if (cell == null) {
            return 0;
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        try {
            return Double.parseDouble(getCellStringValue(cell).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
