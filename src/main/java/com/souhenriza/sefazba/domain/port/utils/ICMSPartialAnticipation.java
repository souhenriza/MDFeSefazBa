package com.souhenriza.sefazba.domain.port.utils;

import java.time.LocalDate;

public interface ICMSPartialAnticipation {
    LocalDate getDueDate(LocalDate mdfeIssueDate, boolean haveMDFe);

    String getValueToPay(String totalValue);
}
