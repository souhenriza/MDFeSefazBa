package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.shared.enums.SearchPeriod.DateRange;

public interface NFeSearchSubmitter {

    void submit(DateRange dateRange);

    boolean isSuccess();

}
