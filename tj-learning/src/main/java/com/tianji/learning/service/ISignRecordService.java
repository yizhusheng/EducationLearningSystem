package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;
import org.springframework.stereotype.Service;


public interface ISignRecordService {
    SignResultVO addSignRecords();

    Byte[] querySignRecords();
}
