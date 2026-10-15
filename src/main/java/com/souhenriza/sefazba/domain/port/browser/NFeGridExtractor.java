package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.domain.model.ExtractionResult;
import com.souhenriza.sefazba.shared.enums.SearchPeriod.DateRange;

public interface NFeGridExtractor {

    ExtractionResult extractAll(DateRange dateRange);
}
