package de.salomax.currencies.model

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.squareup.moshi.JsonClass
import de.salomax.currencies.R

private object Iso4217Codes {
    const val AED = 784
    const val AFN = 971
    const val ALL = 8
    const val AMD = 51
    const val ANG = 532
    const val AOA = 973
    const val ARS = 32
    const val AUD = 36
    const val AWG = 533
    const val AZN = 944
    const val BAM = 977
    const val BBD = 52
    const val BDT = 50
    const val BGN = 975
    const val BHD = 48
    const val BIF = 108
    const val BMD = 60
    const val BND = 96
    const val BOB = 68
    const val BRL = 986
    const val BSD = 44
    const val BTN = 64
    const val BWP = 72
    const val BYN = 933
    const val BZD = 84
    const val CAD = 124
    const val CDF = 976
    const val CHF = 756
    const val CLF = 990
    const val CLP = 152
    const val CNY = 156
    const val COP = 170
    const val CRC = 188
    const val CUC = 931
    const val CUP = 192
    const val CVE = 132
    const val CZK = 203
    const val DJF = 262
    const val DKK = 208
    const val DOP = 214
    const val DZD = 12
    const val EGP = 818
    const val ERN = 232
    const val ETB = 230
    const val EUR = 978
    const val FJD = 242
    const val FKP = 238
    const val GBP = 826
    const val GEL = 981
    const val GHS = 936
    const val GIP = 292
    const val GMD = 270
    const val GNF = 324
    const val GTQ = 320
    const val GYD = 328
    const val HKD = 344
    const val HNL = 340
    const val HRK = 191
    const val HTG = 332
    const val HUF = 348
    const val IDR = 360
    const val ILS = 376
    const val INR = 356
    const val IQD = 368
    const val IRR = 364
    const val ISK = 352
    const val JMD = 388
    const val JOD = 400
    const val JPY = 392
    const val KES = 404
    const val KGS = 417
    const val KHR = 116
    const val KMF = 174
    const val KPW = 408
    const val KRW = 410
    const val KWD = 414
    const val KYD = 136
    const val KZT = 398
    const val LAK = 418
    const val LBP = 422
    const val LKR = 144
    const val LRD = 430
    const val LSL = 426
    const val LYD = 434
    const val MAD = 504
    const val MDL = 498
    const val MGA = 969
    const val MKD = 807
    const val MMK = 104
    const val MNT = 496
    const val MOP = 446
    const val MRO = 478
    const val MRU = 929
    const val MUR = 480
    const val MVR = 462
    const val MWK = 454
    const val MXN = 484
    const val MYR = 458
    const val MZN = 943
    const val NAD = 516
    const val NGN = 566
    const val NIO = 558
    const val NOK = 578
    const val NPR = 524
    const val NZD = 554
    const val OMR = 512
    const val PAB = 590
    const val PEN = 604
    const val PGK = 598
    const val PHP = 608
    const val PKR = 586
    const val PLN = 985
    const val PYG = 600
    const val QAR = 634
    const val RON = 946
    const val RSD = 941
    const val RUB = 643
    const val RWF = 646
    const val SAR = 682
    const val SBD = 90
    const val SCR = 690
    const val SDG = 938
    const val SEK = 752
    const val SGD = 702
    const val SHP = 654
    const val SLE = 925
    const val SLL = 694
    const val SOS = 706
    const val SRD = 968
    const val SSP = 728
    const val STD = 678
    const val STN = 930
    const val SVC = 222
    const val SYP = 760
    const val SZL = 748
    const val THB = 764
    const val TJS = 972
    const val TMT = 934
    const val TND = 788
    const val TOP = 776
    const val TRY = 949
    const val TTD = 780
    const val TWD = 901
    const val TZS = 834
    const val UAH = 980
    const val UGX = 800
    const val USD = 840
    const val UYU = 858
    const val UZS = 860
    const val VEF = 937
    const val VES = 928
    const val VND = 704
    const val VUV = 548
    const val WST = 882
    const val XAF = 950
    const val XAG = 961
    const val XAU = 959
    const val XCD = 951
    const val XDR = 960
    const val XOF = 952
    const val XPD = 964
    const val XPF = 953
    const val XPT = 962
    const val YER = 886
    const val ZAR = 710
    const val ZMW = 967
    const val ZWL = 932
}

@Suppress("unused")
@JsonClass(generateAdapter = false) // see https://stackoverflow.com/a/64085370/421140
enum class Currency(
    private val iso4217Alpha: String,  // USD
    private val iso4217Numeric: Int?,  // 840
    private val symbol: String?,       // $
    private val fullName: Int,         // US dollar
    private val flag: Int?             // vector drawable: star-spangled banner
) {
    AED("AED", Iso4217Codes.AED,  "د.إ",  R.string.name_aed, R.drawable.flag_ae),
    AFN("AFN", Iso4217Codes.AFN,  "؋",    R.string.name_afn, R.drawable.flag_af),
    ALL("ALL", Iso4217Codes.ALL,  "L",    R.string.name_all, R.drawable.flag_al),
    AMD("AMD", Iso4217Codes.AMD,  "֏",    R.string.name_amd, R.drawable.flag_am),
    // Dutch flag: on 10 October 2010, the Netherlands Antilles was dissolved into Curaçao,
    // Sint Maarten and the three public bodies of the Caribbean Netherlands.
    ANG("ANG", Iso4217Codes.ANG,  "ƒ",    R.string.name_ang, R.drawable.flag_nl),
    AOA("AOA", Iso4217Codes.AOA,  "Kz",   R.string.name_aoa, R.drawable.flag_ao),
    ARS("ARS", Iso4217Codes.ARS,  "$",    R.string.name_ars, R.drawable.flag_ar),
    AUD("AUD", Iso4217Codes.AUD,  "$",    R.string.name_aud, R.drawable.flag_au),
    AWG("AWG", Iso4217Codes.AWG,  "Afl.", R.string.name_awg, R.drawable.flag_aw),
    AZN("AZN", Iso4217Codes.AZN,  "₼",    R.string.name_azn, R.drawable.flag_az),
    BAM("BAM", Iso4217Codes.BAM,  "KM",   R.string.name_bam, R.drawable.flag_ba),
    BBD("BBD", Iso4217Codes.BBD,  "$",    R.string.name_bbd, R.drawable.flag_bb),
    BDT("BDT", Iso4217Codes.BDT,  "৳",    R.string.name_bdt, R.drawable.flag_bd),
    BGN("BGN", Iso4217Codes.BGN,  "лв",   R.string.name_bgn, R.drawable.flag_bg),
    BHD("BHD", Iso4217Codes.BHD,  ".د.ب", R.string.name_bhd, R.drawable.flag_bh),
    BIF("BIF", Iso4217Codes.BIF,  "Fr",   R.string.name_bif, R.drawable.flag_bi),
    BMD("BMD", Iso4217Codes.BMD,  "$",    R.string.name_bmd, R.drawable.flag_bm),
    BND("BND", Iso4217Codes.BND,  "$",    R.string.name_bnd, R.drawable.flag_bn),
    BOB("BOB", Iso4217Codes.BOB,  "Bs.",  R.string.name_bob, R.drawable.flag_bo),
    BRL("BRL", Iso4217Codes.BRL,  "R$",   R.string.name_brl, R.drawable.flag_br),
    BSD("BSD", Iso4217Codes.BSD,  "$",    R.string.name_bsd, R.drawable.flag_bs),
    BTC("BTC", null, "₿",    R.string.name_btc, null),
    BTN("BTN", Iso4217Codes.BTN,  "Nu.",  R.string.name_btn, R.drawable.flag_bt),
    BWP("BWP", Iso4217Codes.BWP,  "P",    R.string.name_bwp, R.drawable.flag_bw),
    BYN("BYN", Iso4217Codes.BYN,  "Br",   R.string.name_byn, R.drawable.flag_by),
    BZD("BZD", Iso4217Codes.BZD,  "$",    R.string.name_bzd, R.drawable.flag_bz),
    CAD("CAD", Iso4217Codes.CAD,  "$",    R.string.name_cad, R.drawable.flag_ca),
    CDF("CDF", Iso4217Codes.CDF,  "Fr",   R.string.name_cdf, R.drawable.flag_cd),
    CHF("CHF", Iso4217Codes.CHF,  "Fr.",  R.string.name_chf, R.drawable.flag_ch),
    CLF("CLF", Iso4217Codes.CLF,  null,   R.string.name_clf, R.drawable.flag_cl),
    CLP("CLP", Iso4217Codes.CLP,  "$",    R.string.name_clp, R.drawable.flag_cl),
    CNH("CNH", null, "¥",    R.string.name_cnh, R.drawable.flag_cn),
    CNY("CNY", Iso4217Codes.CNY,  "¥",    R.string.name_cny, R.drawable.flag_cn),
    COP("COP", Iso4217Codes.COP,  "$",    R.string.name_cop, R.drawable.flag_co),
    CRC("CRC", Iso4217Codes.CRC,  "₡",    R.string.name_crc, R.drawable.flag_cr),
    CUC("CUC", Iso4217Codes.CUC,  "$",    R.string.name_cuc, R.drawable.flag_cu),
    CUP("CUP", Iso4217Codes.CUP,  "$",    R.string.name_cup, R.drawable.flag_cu),
    CVE("CVE", Iso4217Codes.CVE,  "$",    R.string.name_cve, R.drawable.flag_cv),
    CZK("CZK", Iso4217Codes.CZK,  "Kč",   R.string.name_czk, R.drawable.flag_cz),
    DJF("DJF", Iso4217Codes.DJF,  "Fr",   R.string.name_djf, R.drawable.flag_dj),
    DKK("DKK", Iso4217Codes.DKK,  "kr",   R.string.name_dkk, R.drawable.flag_dk),
    DOP("DOP", Iso4217Codes.DOP,  "RD$",  R.string.name_dop, R.drawable.flag_do),
    DZD("DZD", Iso4217Codes.DZD,  "د.ج",  R.string.name_dzd, R.drawable.flag_dz),
    EGP("EGP", Iso4217Codes.EGP,  "ج.م",  R.string.name_egp, R.drawable.flag_eg),
    ERN("ERN", Iso4217Codes.ERN,  "Nfk",  R.string.name_ern, R.drawable.flag_er),
    ETB("ETB", Iso4217Codes.ETB,  "Br",   R.string.name_etb, R.drawable.flag_et),
    EUR("EUR", Iso4217Codes.EUR,  "€",    R.string.name_eur, R.drawable.flag_eu),
    FJD("FJD", Iso4217Codes.FJD,  "$",    R.string.name_fjd, R.drawable.flag_fj),
    FKP("FKP", Iso4217Codes.FKP,  "£",    R.string.name_fkp, R.drawable.flag_fk),
    FOK("FOK", null, "kr",   R.string.name_fok, R.drawable.flag_fo),
    GBP("GBP", Iso4217Codes.GBP,  "£",    R.string.name_gbp, R.drawable.flag_gb),
    GEL("GEL", Iso4217Codes.GEL,  "₾",    R.string.name_gel, R.drawable.flag_ge),
    GGP("GGP", null, "£",    R.string.name_ggp, R.drawable.flag_gg),
    GHS("GHS", Iso4217Codes.GHS,  "₵",    R.string.name_ghs, R.drawable.flag_gh),
    GIP("GIP", Iso4217Codes.GIP,  "£",    R.string.name_gip, R.drawable.flag_gi),
    GMD("GMD", Iso4217Codes.GMD,  "D",    R.string.name_gmd, R.drawable.flag_gm),
    GNF("GNF", Iso4217Codes.GNF,  "Fr",   R.string.name_gnf, R.drawable.flag_gn),
    GTQ("GTQ", Iso4217Codes.GTQ,  "Q",    R.string.name_gtq, R.drawable.flag_gt),
    GYD("GYD", Iso4217Codes.GYD,  "$",    R.string.name_gyd, R.drawable.flag_gy),
    HKD("HKD", Iso4217Codes.HKD,  "$",    R.string.name_hkd, R.drawable.flag_hk),
    HNL("HNL", Iso4217Codes.HNL,  "L",    R.string.name_hnl, R.drawable.flag_hn),
    HRK("HRK", Iso4217Codes.HRK,  "kn",   R.string.name_hrk, R.drawable.flag_hr),
    HTG("HTG", Iso4217Codes.HTG,  "G",    R.string.name_htg, R.drawable.flag_ht),
    HUF("HUF", Iso4217Codes.HUF,  "Ft",   R.string.name_huf, R.drawable.flag_hu),
    IDR("IDR", Iso4217Codes.IDR,  "Rp",   R.string.name_idr, R.drawable.flag_id),
    ILS("ILS", Iso4217Codes.ILS,  "₪",    R.string.name_ils, R.drawable.flag_il),
    IMP("IMP", null, "£",    R.string.name_imp, R.drawable.flag_im),
    INR("INR", Iso4217Codes.INR,  "₹",    R.string.name_inr, R.drawable.flag_in),
    IQD("IQD", Iso4217Codes.IQD,  "ع.د",  R.string.name_iqd, R.drawable.flag_iq),
    IRR("IRR", Iso4217Codes.IRR,  "﷼",    R.string.name_irr, R.drawable.flag_ir),
    ISK("ISK", Iso4217Codes.ISK,  "kr",   R.string.name_isk, R.drawable.flag_is),
    JEP("JEP", null, "£",    R.string.name_jep, R.drawable.flag_je),
    JMD("JMD", Iso4217Codes.JMD,  "$",    R.string.name_jmd, R.drawable.flag_jm),
    JOD("JOD", Iso4217Codes.JOD,  "د.أ",  R.string.name_jod, R.drawable.flag_jo),
    JPY("JPY", Iso4217Codes.JPY,  "¥",    R.string.name_jpy, R.drawable.flag_jp),
    KES("KES", Iso4217Codes.KES,  "Sh",   R.string.name_kes, R.drawable.flag_ke),
    KGS("KGS", Iso4217Codes.KGS,  "С̲",    R.string.name_kgs, R.drawable.flag_kg),
    KHR("KHR", Iso4217Codes.KHR,  "៛",    R.string.name_khr, R.drawable.flag_kh),
    KMF("KMF", Iso4217Codes.KMF,  "Fr",   R.string.name_kmf, R.drawable.flag_km),
    KPW("KPW", Iso4217Codes.KPW,  "₩",    R.string.name_kpw, R.drawable.flag_kp),
    KRW("KRW", Iso4217Codes.KRW,  "₩",    R.string.name_krw, R.drawable.flag_kr),
    KWD("KWD", Iso4217Codes.KWD,  "د.ك",  R.string.name_kwd, R.drawable.flag_kw),
    KYD("KYD", Iso4217Codes.KYD,  "$",    R.string.name_kyd, R.drawable.flag_ky),
    KZT("KZT", Iso4217Codes.KZT,  "₸",    R.string.name_kzt, R.drawable.flag_kz),
    LAK("LAK", Iso4217Codes.LAK,  "₭",    R.string.name_lak, R.drawable.flag_la),
    LBP("LBP", Iso4217Codes.LBP,  "ل.ل.", R.string.name_lbp, R.drawable.flag_lb),
    LKR("LKR", Iso4217Codes.LKR,  "Rs",   R.string.name_lkr, R.drawable.flag_lk),
    LRD("LRD", Iso4217Codes.LRD,  "$",    R.string.name_lrd, R.drawable.flag_lr),
    LSL("LSL", Iso4217Codes.LSL,  "L",    R.string.name_lsl, R.drawable.flag_ls),
    LYD("LYD", Iso4217Codes.LYD,  "ل.د",  R.string.name_lyd, R.drawable.flag_ly),
    MAD("MAD", Iso4217Codes.MAD,  "د.م.", R.string.name_mad, R.drawable.flag_ma),
    MDL("MDL", Iso4217Codes.MDL,  "L",    R.string.name_mdl, R.drawable.flag_md),
    MGA("MGA", Iso4217Codes.MGA,  "Ar",   R.string.name_mga, R.drawable.flag_mg),
    MKD("MKD", Iso4217Codes.MKD,  "ден",  R.string.name_mkd, R.drawable.flag_mk),
    MMK("MMK", Iso4217Codes.MMK,  "Ks",   R.string.name_mmk, R.drawable.flag_mm),
    MNT("MNT", Iso4217Codes.MNT,  "₮",    R.string.name_mnt, R.drawable.flag_mn),
    MOP("MOP", Iso4217Codes.MOP,  "MOP$", R.string.name_mop, R.drawable.flag_mo),
    MRO("MRO", Iso4217Codes.MRO,  "UM",   R.string.name_mro, R.drawable.flag_mr),
    MRU("MRU", Iso4217Codes.MRU,  "UM",   R.string.name_mru, R.drawable.flag_mr),
    MUR("MUR", Iso4217Codes.MUR,  "₨",    R.string.name_mur, R.drawable.flag_mu),
    MVR("MVR", Iso4217Codes.MVR,  ".ރ",   R.string.name_mvr, R.drawable.flag_mv),
    MWK("MWK", Iso4217Codes.MWK,  "MK",   R.string.name_mwk, R.drawable.flag_mw),
    MXN("MXN", Iso4217Codes.MXN,  "$",    R.string.name_mxn, R.drawable.flag_mx),
    MYR("MYR", Iso4217Codes.MYR,  "RM",   R.string.name_myr, R.drawable.flag_my),
    MZN("MZN", Iso4217Codes.MZN,  "MT",   R.string.name_mzn, R.drawable.flag_mz),
    NAD("NAD", Iso4217Codes.NAD,  "$",    R.string.name_nad, R.drawable.flag_na),
    NGN("NGN", Iso4217Codes.NGN,  "₦",    R.string.name_ngn, R.drawable.flag_ng),
    NIO("NIO", Iso4217Codes.NIO,  "C$",   R.string.name_nio, R.drawable.flag_ni),
    NOK("NOK", Iso4217Codes.NOK,  "kr",   R.string.name_nok, R.drawable.flag_no),
    NPR("NPR", Iso4217Codes.NPR,  "रु",    R.string.name_npr, R.drawable.flag_np),
    NZD("NZD", Iso4217Codes.NZD,  "$",    R.string.name_nzd, R.drawable.flag_nz),
    OMR("OMR", Iso4217Codes.OMR,  "ر.ع.", R.string.name_omr, R.drawable.flag_om),
    PAB("PAB", Iso4217Codes.PAB,  "B/.",  R.string.name_pab, R.drawable.flag_pa),
    PEN("PEN", Iso4217Codes.PEN,  "S/",   R.string.name_pen, R.drawable.flag_pe),
    PGK("PGK", Iso4217Codes.PGK,  "K",    R.string.name_pgk, R.drawable.flag_pg),
    PHP("PHP", Iso4217Codes.PHP,  "₱",    R.string.name_php, R.drawable.flag_ph),
    PKR("PKR", Iso4217Codes.PKR,  "₨",    R.string.name_pkr, R.drawable.flag_pk),
    PLN("PLN", Iso4217Codes.PLN,  "zł",   R.string.name_pln, R.drawable.flag_pl),
    PYG("PYG", Iso4217Codes.PYG,  "₲",    R.string.name_pyg, R.drawable.flag_py),
    QAR("QAR", Iso4217Codes.QAR,  "ر.ق",  R.string.name_qar, R.drawable.flag_qa),
    RON("RON", Iso4217Codes.RON,  "lei",  R.string.name_ron, R.drawable.flag_ro),
    RSD("RSD", Iso4217Codes.RSD,  "дин.", R.string.name_rsd, R.drawable.flag_rs),
    RUB("RUB", Iso4217Codes.RUB,  "₽",    R.string.name_rub, R.drawable.flag_ru),
    RWF("RWF", Iso4217Codes.RWF,  "Fr",   R.string.name_rwf, R.drawable.flag_rw),
    SAR("SAR", Iso4217Codes.SAR,  "ر.س",  R.string.name_sar, R.drawable.flag_sa),
    SBD("SBD", Iso4217Codes.SBD,  " $",   R.string.name_sbd, R.drawable.flag_sb),
    SCR("SCR", Iso4217Codes.SCR,  "₨",    R.string.name_scr, R.drawable.flag_sc),
    SDG("SDG", Iso4217Codes.SDG,  "ج.س.", R.string.name_sdg, R.drawable.flag_sd),
    SEK("SEK", Iso4217Codes.SEK,  "kr",   R.string.name_sek, R.drawable.flag_se),
    SGD("SGD", Iso4217Codes.SGD,  "$",    R.string.name_sgd, R.drawable.flag_sg),
    SHP("SHP", Iso4217Codes.SHP,  "£",    R.string.name_shp, R.drawable.flag_sh),
    SLE("SLE", Iso4217Codes.SLE,  "Le",   R.string.name_sle, R.drawable.flag_sl),
    SLL("SLL", Iso4217Codes.SLL,  "Le",   R.string.name_sll, R.drawable.flag_sl),
    SOS("SOS", Iso4217Codes.SOS,  "Sh",   R.string.name_sos, R.drawable.flag_so),
    SRD("SRD", Iso4217Codes.SRD,  "$",    R.string.name_srd, R.drawable.flag_sr),
    SSP("SSP", Iso4217Codes.SSP,  "£",    R.string.name_ssp, R.drawable.flag_ss),
    STD("STD", Iso4217Codes.STD,  "Db",   R.string.name_std, R.drawable.flag_st),
    STN("STN", Iso4217Codes.STN,  "Db",   R.string.name_stn, R.drawable.flag_st),
    SVC("SVC", Iso4217Codes.SVC,  "₡",    R.string.name_svc, R.drawable.flag_sv),
    SYP("SYP", Iso4217Codes.SYP,  "ل.س",  R.string.name_syp, R.drawable.flag_sy),
    SZL("SZL", Iso4217Codes.SZL,  "L",    R.string.name_szl, R.drawable.flag_sz),
    THB("THB", Iso4217Codes.THB,  "฿",    R.string.name_thb, R.drawable.flag_th),
    TJS("TJS", Iso4217Codes.TJS,  "SM",   R.string.name_tjs, R.drawable.flag_tj),
    TMT("TMT", Iso4217Codes.TMT,  "m",    R.string.name_tmt, R.drawable.flag_tm),
    TND("TND", Iso4217Codes.TND,  "د.ت",  R.string.name_tnd, R.drawable.flag_tn),
    TOP("TOP", Iso4217Codes.TOP,  "T$",   R.string.name_top, R.drawable.flag_to),
    TRY("TRY", Iso4217Codes.TRY,  "₺",    R.string.name_try, R.drawable.flag_tr),
    TTD("TTD", Iso4217Codes.TTD,  "$",    R.string.name_ttd, R.drawable.flag_tt),
    TWD("TWD", Iso4217Codes.TWD,  "$",    R.string.name_twd, R.drawable.flag_tw),
    TZS("TZS", Iso4217Codes.TZS,  "Sh",   R.string.name_tzs, R.drawable.flag_tz),
    UAH("UAH", Iso4217Codes.UAH,  "₴",    R.string.name_uah, R.drawable.flag_ua),
    UGX("UGX", Iso4217Codes.UGX,  "Sh",   R.string.name_ugx, R.drawable.flag_ug),
    USD("USD", Iso4217Codes.USD,  "$",    R.string.name_usd, R.drawable.flag_us),
    UYU("UYU", Iso4217Codes.UYU,  "$",    R.string.name_uyu, R.drawable.flag_uy),
    UZS("UZS", Iso4217Codes.UZS,  "сўм",  R.string.name_uzs, R.drawable.flag_uz),
    VEF("VEF", Iso4217Codes.VEF,  "Bs.",  R.string.name_vef, R.drawable.flag_ve),
    VES("VES", Iso4217Codes.VES,  "Bs.",  R.string.name_ves, R.drawable.flag_ve),
    VND("VND", Iso4217Codes.VND,  "₫",    R.string.name_vnd, R.drawable.flag_vn),
    VUV("VUV", Iso4217Codes.VUV,  "Vt",   R.string.name_vuv, R.drawable.flag_vu),
    WST("WST", Iso4217Codes.WST,  "T",    R.string.name_wst, R.drawable.flag_ws),
    XAF("XAF", Iso4217Codes.XAF,  "Fr",   R.string.name_xaf, null),
    XAG("XAG", Iso4217Codes.XAG,  null,   R.string.name_xag, null),
    XAU("XAU", Iso4217Codes.XAU,  null,   R.string.name_xau, null),
    XCD("XCD", Iso4217Codes.XCD,  "$",    R.string.name_xcd, null),
    XDR("XDR", Iso4217Codes.XDR,  null,   R.string.name_xdr, null),
    XOF("XOF", Iso4217Codes.XOF,  "Fr",   R.string.name_xof, null),
    XPD("XPD", Iso4217Codes.XPD,  null,   R.string.name_xpd, null),
    XPF("XPF", Iso4217Codes.XPF,  "₣",    R.string.name_xpf, null),
    XPT("XPT", Iso4217Codes.XPT,  null,   R.string.name_xpt, null),
    YER("YER", Iso4217Codes.YER,  "ر.ي",  R.string.name_yer, R.drawable.flag_ye),
    ZAR("ZAR", Iso4217Codes.ZAR,  "R",    R.string.name_zar, R.drawable.flag_za),
    ZMW("ZMW", Iso4217Codes.ZMW,  "ZK",   R.string.name_zmw, R.drawable.flag_zm),
    ZWL("ZWL", Iso4217Codes.ZWL,  "$",    R.string.name_zwl, R.drawable.flag_zw);

    companion object {
        // non-tradeable / superseded / special currencies excluded from conversion
        private val excluded = setOf(
            "BTC",             // Bitcoin
            "XAG", "XAU", "XPD", "XPT", // metals
            "MRO", "STD", "VEF", "CUC", // superseded
            "XDR", "CLF", "CNH"         // special / offshore
        )

        fun fromString(value: String): Currency? =
            if (value !in excluded)
                entries.firstOrNull { it.iso4217Alpha == value }
            else null
    }

    /**
     * https://en.wikipedia.org/wiki/ISO_4217#Alpha_codes
     * e.g. USD
     */
    fun iso4217Alpha(): String {
        return this.iso4217Alpha
    }

    /**
     * https://en.wikipedia.org/wiki/ISO_4217#Numeric_codes
     * e.g. 840 for USD
     */
    @Suppress("unused")
    fun iso4217Numeric(): Int? {
        return this.iso4217Numeric
    }

    /**
     * e.g. US dollar (localized) for USD
     */
    fun fullName(context: Context): String {
        return context.getString(this.fullName)
    }

    /**
     * e.g. star-spangled banner for USD
     */
    fun flag(context: Context): Drawable {
        return ContextCompat.getDrawable(context, this.flag ?: R.drawable.flag_unknown)!!
    }

    /**
     * https://en.wikipedia.org/wiki/Currency_symbol
     * e.g. $ for USD
     */
    fun symbol(): String? {
        return this.symbol
            ?.let { if (it.hasRtlChar()) it.wrapLtr() else it }
    }

    /**
     * https://en.wikipedia.org/wiki/Bidirectional_text#Table_of_possible_BiDi_character_types
     */
    private fun String.wrapLtr(): String {
        // isolate (recommended, but too new - FSI + PDI)
        // return "\u2067" + this + "\u2069"
        // embedding (discouraged - LRE + PDF)
        return "\u202A" + this + "\u202C"
    }

    private fun String.hasRtlChar(): Boolean {
        return this.any { it.directionality == CharDirectionality.RIGHT_TO_LEFT_ARABIC }
    }

}
