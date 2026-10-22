package com.souhenriza.sefazba.domain.impl;

import com.souhenriza.sefazba.domain.port.utils.ICMSPartialAnticipation;

import java.time.LocalDate;

public class ICMSPartialAnticipationImpl implements ICMSPartialAnticipation {


    @Override
    public LocalDate getDueDate(LocalDate nfeIssueDate, boolean haveMDFe) {
        if (!haveMDFe) {
            return nfeIssueDate;
        }


        return LocalDate.of(nfeIssueDate.getYear(), nfeIssueDate.plusMonths(1).getMonth(), 25);
    }

}
