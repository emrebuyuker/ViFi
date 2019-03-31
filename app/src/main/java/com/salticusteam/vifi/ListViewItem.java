package com.salticusteam.vifi;

public class ListViewItem {
    private String solUst;
    private String orta;
    private String sagAlt;
    private String solAlt;

    public ListViewItem(String solUst, String orta, String sagAlt, String solAlt) {
        this.solUst = solUst;
        this.orta = orta;
        this.sagAlt = sagAlt;
        this.solAlt = solAlt;
    }

    public String getSolUst() {
        return solUst;
    }

    public void getSolUst(String solUst) {
        this.solUst = solUst;
    }

    public String getOrta() {
        return orta;
    }

    public void setOrta(String orta) {
        this.orta = orta;
    }

    public String getSagAlt() {
        return sagAlt;
    }

    public void setSagAlt(String sagAlt) {
        this.sagAlt = sagAlt;
    }

    public String getSolAlt() {
        return solAlt;
    }

    public void setSolAlt(String sagAlt) {
        this.solAlt = solAlt;
    }
}
