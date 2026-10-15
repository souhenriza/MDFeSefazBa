package com.souhenriza.sefazba.domain.impl;

import com.souhenriza.sefazba.domain.port.utils.ICMSPartialAnticipation;

import java.time.LocalDate;

public class ICMSPartialAnticipationImpl implements ICMSPartialAnticipation {


    @Override
    public LocalDate getDueDate(LocalDate mdfeIssueDate, boolean haveMDFe) {
        if (!haveMDFe) {
            return mdfeIssueDate;
        }


        return LocalDate.of(mdfeIssueDate.getYear(), mdfeIssueDate.plusMonths(1).getMonth(), 25);
    }

    @Override
    public String getValueToPay(String totalValue) {
        return "";
    }
}
