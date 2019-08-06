package com.shaft.gui.browser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.testng.Assert;

import com.shaft.cli.FileActions;
import com.shaft.gui.element.JSWaiter;
import com.shaft.gui.image.ScreenshotManager;
import com.shaft.tools.io.ReportManager;

import io.github.bonigarcia.wdm.WebDriverManager;

public class BrowserFactory {

    private static final Boolean AUTO_MAXIMIZE = Boolean
	    .valueOf(System.getProperty("autoMaximizeBrowserWindow").trim());
    private static final Boolean HEADLESS_EXECUTION = Boolean.valueOf(System.getProperty("headlessExecution").trim());
    private static final String EXECUTION_ADDRESS = System.getProperty("executionAddress").trim();
    // local OR hub ip:port
    private static final String TARGET_HUB_URL = "http://" + EXECUTION_ADDRESS + "/wd/hub";
    private static String targetOperatingSystem = System.getProperty("targetOperatingSystem");
    // Windows-64 | Linux-64 | Mac-64
    private static final String TARGET_BROWSER_NAME = System.getProperty("targetBrowserName");
    // Default | MozillaFirefox | MicrosoftInternetExplorer | GoogleChrome |
    // MicrosoftEdge | Safari
    private static final int PAGE_LOAD_TIMEOUT = 30;
    private static final int IMPLICIT_WAIT_TIMEOUT = 30;
    private static final Boolean WAIT_IMPLICITLY = Boolean.valueOf(System.getProperty("waitImplicitly").trim());
    private static final Boolean CREATE_GIF = Boolean.valueOf(System.getProperty("createAnimatedGif").trim());
    private static final Boolean BROWSEROBJECTSINGLETON = Boolean
	    .valueOf(System.getProperty("browserObjectSingleton").trim());

    private static String customDriverPath = System.getProperty("customDriverPath");
    private static String customDriverName = System.getProperty("customDriverName");

    private static Map<String, Map<String, WebDriver>> drivers = new HashMap<>();
    // browser, <os,driver>
    private static WebDriver driver = null;

    // logging preferences object
    private static LoggingPreferences logPrefs;

    // supported browser options
    private static ChromeOptions chOptions;
    private static FirefoxOptions ffOptions;
    private static SafariOptions sfOptions;
    private static EdgeOptions edOptions;
    private static InternetExplorerOptions ieOptions;

    // supported browser types
    public enum BrowserType {
	MOZILLA_FIREFOX("MozillaFirefox"), GOOGLE_CHROME("GoogleChrome"), APPLE_SAFARI("Safari"),
	MICROSOFT_IE("MicrosoftInternetExplorer"), MICROSOFT_EDGE("MicrosoftEdge");

	private String value;

	BrowserType(String type) {
	    this.value = type;
	}

	protected String getValue() {
	    return value;
	}
    }

    // supported operating systems
    public enum OperatingSystemType {
	LINUX("Linux-64"), MACOS("Mac-64"), WINDOWS("Windows-64");

	private String value;

	OperatingSystemType(String type) {
	    this.value = type;
	}

	protected String getValue() {
	    return value;
	}
    }

    // kill-switch
    private static boolean killSwitch = false;

    private BrowserFactory() {
	throw new IllegalStateException("Utility class");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [private] Reporting Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static void failAction(String actionName, String testData) {
	String message = "[" + actionName + "] failed.";
	if (testData != null) {
	    message = message + " With the following test data [" + testData + "].";
	}
	ReportManager.log(message);
	Assert.fail(message);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [private] Preparation and Support Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static BrowserType getBrowserTypeFromName(String browserName) {
	int values = BrowserType.values().length;
	for (int i = 0; i < values; i++) {
	    if (Arrays.asList(BrowserType.values()).get(i).getValue().equalsIgnoreCase(browserName.trim())) {
		return Arrays.asList(BrowserType.values()).get(i);
	    }
	}
	failAction("getBrowserTypeFromName", "Unsupported Browser Type [" + browserName + "].");
	return BrowserType.GOOGLE_CHROME;
    }

    private static OperatingSystemType getOperatingSystemFromName(String operatingSystemName) {
	int values = OperatingSystemType.values().length;
	for (int i = 0; i < values; i++) {
	    if (Arrays.asList(OperatingSystemType.values()).get(i).getValue()
		    .equalsIgnoreCase(operatingSystemName.trim())) {
		return Arrays.asList(OperatingSystemType.values()).get(i);
	    }
	}
	failAction("OperatingSystemType", "Unsupported Operating System [" + targetOperatingSystem + "].");
	return OperatingSystemType.LINUX;
    }

    /**
     * Check cross-compatibility between the selected operating system and browser
     * and report in case they are not compatible
     */
    private static void checkBrowserOSCrossCompatibility(String browserName) {
	Boolean isCompatibleBrowser = false;
	BrowserType browserType = getBrowserTypeFromName(browserName);
	OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);

	switch (operatingSystem) {
	case WINDOWS:
	    if (browserType.equals(BrowserType.MOZILLA_FIREFOX) || browserType.equals(BrowserType.GOOGLE_CHROME)
		    || browserType.equals(BrowserType.MICROSOFT_IE) || browserType.equals(BrowserType.MICROSOFT_EDGE)) {
		isCompatibleBrowser = true;
	    }
	    break;
	case LINUX:
	    if (browserType.equals(BrowserType.MOZILLA_FIREFOX) || browserType.equals(BrowserType.GOOGLE_CHROME)) {
		isCompatibleBrowser = true;
	    }
	    break;
	case MACOS:
	    if (browserType.equals(BrowserType.MOZILLA_FIREFOX) || browserType.equals(BrowserType.GOOGLE_CHROME)
		    || browserType.equals(BrowserType.APPLE_SAFARI)) {
		isCompatibleBrowser = true;
	    }
	    break;
	default:
	    failAction("checkBrowserOSCrossCompatibility",
		    "Unsupported Operating System [" + targetOperatingSystem + "].");
	    break;
	}

	if (!isCompatibleBrowser) {
	    failAction("checkBrowserOSCrossCompatibility", "Unsupported Browser Type [" + browserName
		    + "] for this Operating System [" + targetOperatingSystem + "].");
	}

    }

    private static String setDriversExtecutableFileExtension() {
	OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);
	if (operatingSystem.equals(OperatingSystemType.WINDOWS)) {
	    return ".exe";
	} else {
	    return "";
	}
    }

    private static void setLoggingPrefrences() {
	logPrefs = new LoggingPreferences();
	logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
	logPrefs.enable(LogType.BROWSER, Level.ALL);
	logPrefs.enable(LogType.DRIVER, Level.ALL);
    }

    private static void setDriverOptions(String browserName) {
	String downloadsFolderPath = FileActions.getAbsolutePath(System.getProperty("downloadsFolderPath"));
	BrowserType browserType = getBrowserTypeFromName(browserName);

	switch (browserType) {
	case MOZILLA_FIREFOX:
	    ffOptions = new FirefoxOptions();
	    ffOptions.setCapability("platform", getDesiredOperatingSystem());
	    ffOptions.setCapability("nativeEvents", true);
	    ffOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
	    if (HEADLESS_EXECUTION) {
		// https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Headless_mode
		ffOptions.addArguments("-headless");
	    }
	    FirefoxProfile ffProfile = new FirefoxProfile();
	    ffProfile.setPreference("browser.download.dir", downloadsFolderPath);
	    ffProfile.setPreference("browser.download.folderList", 2);
	    ffProfile.setPreference("browser.helperApps.neverAsk.saveToDisk",
		    "application/vnd.hzn-3d-crossword;video/3gpp;video/3gpp2;application/vnd.mseq;application/vnd.3m.post-it-notes;application/vnd.3gpp.pic-bw-large;application/vnd.3gpp.pic-bw-small;application/vnd.3gpp.pic-bw-var;application/vnd.3gp2.tcap;application/x-7z-compressed;application/x-abiword;application/x-ace-compressed;application/vnd.americandynamics.acc;application/vnd.acucobol;application/vnd.acucorp;audio/adpcm;application/x-authorware-bin;application/x-athorware-map;application/x-authorware-seg;application/vnd.adobe.air-application-installer-package+zip;application/x-shockwave-flash;application/vnd.adobe.fxp;application/pdf;application/vnd.cups-ppd;application/x-director;applicaion/vnd.adobe.xdp+xml;application/vnd.adobe.xfdf;audio/x-aac;application/vnd.ahead.space;application/vnd.airzip.filesecure.azf;application/vnd.airzip.filesecure.azs;application/vnd.amazon.ebook;application/vnd.amiga.ami;applicatin/andrew-inset;application/vnd.android.package-archive;application/vnd.anser-web-certificate-issue-initiation;application/vnd.anser-web-funds-transfer-initiation;application/vnd.antix.game-component;application/vnd.apple.installe+xml;application/applixware;application/vnd.hhe.lesson-player;application/vnd.aristanetworks.swi;text/x-asm;application/atomcat+xml;application/atomsvc+xml;application/atom+xml;application/pkix-attr-cert;audio/x-aiff;video/x-msvieo;application/vnd.audiograph;image/vnd.dxf;model/vnd.dwf;text/plain-bas;application/x-bcpio;application/octet-stream;image/bmp;application/x-bittorrent;application/vnd.rim.cod;application/vnd.blueice.multipass;application/vnd.bm;application/x-sh;image/prs.btif;application/vnd.businessobjects;application/x-bzip;application/x-bzip2;application/x-csh;text/x-c;application/vnd.chemdraw+xml;text/css;chemical/x-cdx;chemical/x-cml;chemical/x-csml;application/vn.contact.cmsg;application/vnd.claymore;application/vnd.clonk.c4group;image/vnd.dvb.subtitle;application/cdmi-capability;application/cdmi-container;application/cdmi-domain;application/cdmi-object;application/cdmi-queue;applicationvnd.cluetrust.cartomobile-config;application/vnd.cluetrust.cartomobile-config-pkg;image/x-cmu-raster;model/vnd.collada+xml;text/csv;application/mac-compactpro;application/vnd.wap.wmlc;image/cgm;x-conference/x-cooltalk;image/x-cmx;application/vnd.xara;application/vnd.cosmocaller;application/x-cpio;application/vnd.crick.clicker;application/vnd.crick.clicker.keyboard;application/vnd.crick.clicker.palette;application/vnd.crick.clicker.template;application/vn.crick.clicker.wordbank;application/vnd.criticaltools.wbs+xml;application/vnd.rig.cryptonote;chemical/x-cif;chemical/x-cmdf;application/cu-seeme;application/prs.cww;text/vnd.curl;text/vnd.curl.dcurl;text/vnd.curl.mcurl;text/vnd.crl.scurl;application/vnd.curl.car;application/vnd.curl.pcurl;application/vnd.yellowriver-custom-menu;application/dssc+der;application/dssc+xml;application/x-debian-package;audio/vnd.dece.audio;image/vnd.dece.graphic;video/vnd.dec.hd;video/vnd.dece.mobile;video/vnd.uvvu.mp4;video/vnd.dece.pd;video/vnd.dece.sd;video/vnd.dece.video;application/x-dvi;application/vnd.fdsn.seed;application/x-dtbook+xml;application/x-dtbresource+xml;application/vnd.dvb.ait;applcation/vnd.dvb.service;audio/vnd.digital-winds;image/vnd.djvu;application/xml-dtd;application/vnd.dolby.mlp;application/x-doom;application/vnd.dpgraph;audio/vnd.dra;application/vnd.dreamfactory;audio/vnd.dts;audio/vnd.dts.hd;imag/vnd.dwg;application/vnd.dynageo;application/ecmascript;application/vnd.ecowin.chart;image/vnd.fujixerox.edmics-mmr;image/vnd.fujixerox.edmics-rlc;application/exi;application/vnd.proteus.magazine;application/epub+zip;message/rfc82;application/vnd.enliven;application/vnd.is-xpr;image/vnd.xiff;application/vnd.xfdl;application/emma+xml;application/vnd.ezpix-album;application/vnd.ezpix-package;image/vnd.fst;video/vnd.fvt;image/vnd.fastbidsheet;application/vn.denovo.fcselayout-link;video/x-f4v;video/x-flv;image/vnd.fpx;image/vnd.net-fpx;text/vnd.fmi.flexstor;video/x-fli;application/vnd.fluxtime.clip;application/vnd.fdf;text/x-fortran;application/vnd.mif;application/vnd.framemaker;imae/x-freehand;application/vnd.fsc.weblaunch;application/vnd.frogans.fnc;application/vnd.frogans.ltf;application/vnd.fujixerox.ddd;application/vnd.fujixerox.docuworks;application/vnd.fujixerox.docuworks.binder;application/vnd.fujitu.oasys;application/vnd.fujitsu.oasys2;application/vnd.fujitsu.oasys3;application/vnd.fujitsu.oasysgp;application/vnd.fujitsu.oasysprs;application/x-futuresplash;application/vnd.fuzzysheet;image/g3fax;application/vnd.gmx;model/vn.gtw;application/vnd.genomatix.tuxedo;application/vnd.geogebra.file;application/vnd.geogebra.tool;model/vnd.gdl;application/vnd.geometry-explorer;application/vnd.geonext;application/vnd.geoplan;application/vnd.geospace;applicatio/x-font-ghostscript;application/x-font-bdf;application/x-gtar;application/x-texinfo;application/x-gnumeric;application/vnd.google-earth.kml+xml;application/vnd.google-earth.kmz;application/vnd.grafeq;image/gif;text/vnd.graphviz;aplication/vnd.groove-account;application/vnd.groove-help;application/vnd.groove-identity-message;application/vnd.groove-injector;application/vnd.groove-tool-message;application/vnd.groove-tool-template;application/vnd.groove-vcar;video/h261;video/h263;video/h264;application/vnd.hp-hpid;application/vnd.hp-hps;application/x-hdf;audio/vnd.rip;application/vnd.hbci;application/vnd.hp-jlyt;application/vnd.hp-pcl;application/vnd.hp-hpgl;application/vnd.yamaha.h-script;application/vnd.yamaha.hv-dic;application/vnd.yamaha.hv-voice;application/vnd.hydrostatix.sof-data;application/hyperstudio;application/vnd.hal+xml;text/html;application/vnd.ibm.rights-management;application/vnd.ibm.securecontainer;text/calendar;application/vnd.iccprofile;image/x-icon;application/vnd.igloader;image/ief;application/vnd.immervision-ivp;application/vnd.immervision-ivu;application/reginfo+xml;text/vnd.in3d.3dml;text/vnd.in3d.spot;mode/iges;application/vnd.intergeo;application/vnd.cinderella;application/vnd.intercon.formnet;application/vnd.isac.fcs;application/ipfix;application/pkix-cert;application/pkixcmp;application/pkix-crl;application/pkix-pkipath;applicaion/vnd.insors.igm;application/vnd.ipunplugged.rcprofile;application/vnd.irepository.package+xml;text/vnd.sun.j2me.app-descriptor;application/java-archive;application/java-vm;application/x-java-jnlp-file;application/java-serializd-object;text/x-java-source,java;application/javascript;application/json;application/vnd.joost.joda-archive;video/jpm;image/jpeg;video/jpeg;application/vnd.kahootz;application/vnd.chipnuts.karaoke-mmd;application/vnd.kde.karbon;aplication/vnd.kde.kchart;application/vnd.kde.kformula;application/vnd.kde.kivio;application/vnd.kde.kontour;application/vnd.kde.kpresenter;application/vnd.kde.kspread;application/vnd.kde.kword;application/vnd.kenameaapp;applicatin/vnd.kidspiration;application/vnd.kinar;application/vnd.kodak-descriptor;application/vnd.las.las+xml;application/x-latex;application/vnd.llamagraphics.life-balance.desktop;application/vnd.llamagraphics.life-balance.exchange+xml;application/vnd.jam;application/vnd.lotus-1-2-3;application/vnd.lotus-approach;application/vnd.lotus-freelance;application/vnd.lotus-notes;application/vnd.lotus-organizer;application/vnd.lotus-screencam;application/vnd.lotus-wordro;audio/vnd.lucent.voice;audio/x-mpegurl;video/x-m4v;application/mac-binhex40;application/vnd.macports.portpkg;application/vnd.osgeo.mapguide.package;application/marc;application/marcxml+xml;application/mxf;application/vnd.wolfrm.player;application/mathematica;application/mathml+xml;application/mbox;application/vnd.medcalcdata;application/mediaservercontrol+xml;application/vnd.mediastation.cdkey;application/vnd.mfer;application/vnd.mfmp;model/mesh;appliation/mads+xml;application/mets+xml;application/mods+xml;application/metalink4+xml;application/vnd.ms-powerpoint.template.macroenabled.12;application/vnd.ms-word.document.macroenabled.12;application/vnd.ms-word.template.macroenabed.12;application/vnd.mcd;application/vnd.micrografx.flo;application/vnd.micrografx.igx;application/vnd.eszigno3+xml;application/x-msaccess;video/x-ms-asf;application/x-msdownload;application/vnd.ms-artgalry;application/vnd.ms-ca-compressed;application/vnd.ms-ims;application/x-ms-application;application/x-msclip;image/vnd.ms-modi;application/vnd.ms-fontobject;application/vnd.ms-excel;application/vnd.ms-excel.addin.macroenabled.12;application/vnd.ms-excelsheet.binary.macroenabled.12;application/vnd.ms-excel.template.macroenabled.12;application/vnd.ms-excel.sheet.macroenabled.12;application/vnd.ms-htmlhelp;application/x-mscardfile;application/vnd.ms-lrm;application/x-msmediaview;aplication/x-msmoney;application/vnd.openxmlformats-officedocument.presentationml.presentation;application/vnd.openxmlformats-officedocument.presentationml.slide;application/vnd.openxmlformats-officedocument.presentationml.slideshw;application/vnd.openxmlformats-officedocument.presentationml.template;application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;application/vnd.openxmlformats-officedocument.spreadsheetml.template;application/vnd.openxmformats-officedocument.wordprocessingml.document;application/vnd.openxmlformats-officedocument.wordprocessingml.template;application/x-msbinder;application/vnd.ms-officetheme;application/onenote;audio/vnd.ms-playready.media.pya;vdeo/vnd.ms-playready.media.pyv;application/vnd.ms-powerpoint;application/vnd.ms-powerpoint.addin.macroenabled.12;application/vnd.ms-powerpoint.slide.macroenabled.12;application/vnd.ms-powerpoint.presentation.macroenabled.12;appliation/vnd.ms-powerpoint.slideshow.macroenabled.12;application/vnd.ms-project;application/x-mspublisher;application/x-msschedule;application/x-silverlight-app;application/vnd.ms-pki.stl;application/vnd.ms-pki.seccat;application/vn.visio;video/x-ms-wm;audio/x-ms-wma;audio/x-ms-wax;video/x-ms-wmx;application/x-ms-wmd;application/vnd.ms-wpl;application/x-ms-wmz;video/x-ms-wmv;video/x-ms-wvx;application/x-msmetafile;application/x-msterminal;application/msword;application/x-mswrite;application/vnd.ms-works;application/x-ms-xbap;application/vnd.ms-xpsdocument;audio/midi;application/vnd.ibm.minipay;application/vnd.ibm.modcap;application/vnd.jcp.javame.midlet-rms;application/vnd.tmobile-ivetv;application/x-mobipocket-ebook;application/vnd.mobius.mbk;application/vnd.mobius.dis;application/vnd.mobius.plc;application/vnd.mobius.mqy;application/vnd.mobius.msl;application/vnd.mobius.txf;application/vnd.mobius.daf;tex/vnd.fly;application/vnd.mophun.certificate;application/vnd.mophun.application;video/mj2;audio/mpeg;video/vnd.mpegurl;video/mpeg;application/mp21;audio/mp4;video/mp4;application/mp4;application/vnd.apple.mpegurl;application/vnd.msician;application/vnd.muvee.style;application/xv+xml;application/vnd.nokia.n-gage.data;application/vnd.nokia.n-gage.symbian.install;application/x-dtbncx+xml;application/x-netcdf;application/vnd.neurolanguage.nlu;application/vnd.na;application/vnd.noblenet-directory;application/vnd.noblenet-sealer;application/vnd.noblenet-web;application/vnd.nokia.radio-preset;application/vnd.nokia.radio-presets;text/n3;application/vnd.novadigm.edm;application/vnd.novadim.edx;application/vnd.novadigm.ext;application/vnd.flographit;audio/vnd.nuera.ecelp4800;audio/vnd.nuera.ecelp7470;audio/vnd.nuera.ecelp9600;application/oda;application/ogg;audio/ogg;video/ogg;application/vnd.oma.dd2+xml;applicatin/vnd.oasis.opendocument.text-web;application/oebps-package+xml;application/vnd.intu.qbo;application/vnd.openofficeorg.extension;application/vnd.yamaha.openscoreformat;audio/webm;video/webm;application/vnd.oasis.opendocument.char;application/vnd.oasis.opendocument.chart-template;application/vnd.oasis.opendocument.database;application/vnd.oasis.opendocument.formula;application/vnd.oasis.opendocument.formula-template;application/vnd.oasis.opendocument.grapics;application/vnd.oasis.opendocument.graphics-template;application/vnd.oasis.opendocument.image;application/vnd.oasis.opendocument.image-template;application/vnd.oasis.opendocument.presentation;application/vnd.oasis.opendocumen.presentation-template;application/vnd.oasis.opendocument.spreadsheet;application/vnd.oasis.opendocument.spreadsheet-template;application/vnd.oasis.opendocument.text;application/vnd.oasis.opendocument.text-master;application/vnd.asis.opendocument.text-template;image/ktx;application/vnd.sun.xml.calc;application/vnd.sun.xml.calc.template;application/vnd.sun.xml.draw;application/vnd.sun.xml.draw.template;application/vnd.sun.xml.impress;application/vnd.sun.xl.impress.template;application/vnd.sun.xml.math;application/vnd.sun.xml.writer;application/vnd.sun.xml.writer.global;application/vnd.sun.xml.writer.template;application/x-font-otf;application/vnd.yamaha.openscoreformat.osfpvg+xml;application/vnd.osgi.dp;application/vnd.palm;text/x-pascal;application/vnd.pawaafile;application/vnd.hp-pclxl;application/vnd.picsel;image/x-pcx;image/vnd.adobe.photoshop;application/pics-rules;image/x-pict;application/x-chat;aplication/pkcs10;application/x-pkcs12;application/pkcs7-mime;application/pkcs7-signature;application/x-pkcs7-certreqresp;application/x-pkcs7-certificates;application/pkcs8;application/vnd.pocketlearn;image/x-portable-anymap;image/-portable-bitmap;application/x-font-pcf;application/font-tdpfr;application/x-chess-pgn;image/x-portable-graymap;image/png;image/x-portable-pixmap;application/pskc+xml;application/vnd.ctc-posml;application/postscript;application/xfont-type1;application/vnd.powerbuilder6;application/pgp-encrypted;application/pgp-signature;application/vnd.previewsystems.box;application/vnd.pvi.ptid1;application/pls+xml;application/vnd.pg.format;application/vnd.pg.osasli;tex/prs.lines.tag;application/x-font-linux-psf;application/vnd.publishare-delta-tree;application/vnd.pmi.widget;application/vnd.quark.quarkxpress;application/vnd.epson.esf;application/vnd.epson.msf;application/vnd.epson.ssf;applicaton/vnd.epson.quickanime;application/vnd.intu.qfx;video/quicktime;application/x-rar-compressed;audio/x-pn-realaudio;audio/x-pn-realaudio-plugin;application/rsd+xml;application/vnd.rn-realmedia;application/vnd.realvnc.bed;applicatin/vnd.recordare.musicxml;application/vnd.recordare.musicxml+xml;application/relax-ng-compact-syntax;application/vnd.data-vision.rdz;application/rdf+xml;application/vnd.cloanto.rp9;application/vnd.jisp;application/rtf;text/richtex;application/vnd.route66.link66+xml;application/rss+xml;application/shf+xml;application/vnd.sailingtracker.track;image/svg+xml;application/vnd.sus-calendar;application/sru+xml;application/set-payment-initiation;application/set-reistration-initiation;application/vnd.sema;application/vnd.semd;application/vnd.semf;application/vnd.seemail;application/x-font-snf;application/scvp-vp-request;application/scvp-vp-response;application/scvp-cv-request;application/svp-cv-response;application/sdp;text/x-setext;video/x-sgi-movie;application/vnd.shana.informed.formdata;application/vnd.shana.informed.formtemplate;application/vnd.shana.informed.interchange;application/vnd.shana.informed.package;application/thraud+xml;application/x-shar;image/x-rgb;application/vnd.epson.salt;application/vnd.accpac.simply.aso;application/vnd.accpac.simply.imp;application/vnd.simtech-mindmapper;application/vnd.commonspace;application/vnd.ymaha.smaf-audio;application/vnd.smaf;application/vnd.yamaha.smaf-phrase;application/vnd.smart.teacher;application/vnd.svd;application/sparql-query;application/sparql-results+xml;application/srgs;application/srgs+xml;application/sml+xml;application/vnd.koan;text/sgml;application/vnd.stardivision.calc;application/vnd.stardivision.draw;application/vnd.stardivision.impress;application/vnd.stardivision.math;application/vnd.stardivision.writer;application/vnd.tardivision.writer-global;application/vnd.stepmania.stepchart;application/x-stuffit;application/x-stuffitx;application/vnd.solent.sdkm+xml;application/vnd.olpc-sugar;audio/basic;application/vnd.wqd;application/vnd.symbian.install;application/smil+xml;application/vnd.syncml+xml;application/vnd.syncml.dm+wbxml;application/vnd.syncml.dm+xml;application/x-sv4cpio;application/x-sv4crc;application/sbml+xml;text/tab-separated-values;image/tiff;application/vnd.to.intent-module-archive;application/x-tar;application/x-tcl;application/x-tex;application/x-tex-tfm;application/tei+xml;text/plain;application/vnd.spotfire.dxp;application/vnd.spotfire.sfs;application/timestamped-data;applicationvnd.trid.tpt;application/vnd.triscape.mxs;text/troff;application/vnd.trueapp;application/x-font-ttf;text/turtle;application/vnd.umajin;application/vnd.uoml+xml;application/vnd.unity;application/vnd.ufdl;text/uri-list;application/nd.uiq.theme;application/x-ustar;text/x-uuencode;text/x-vcalendar;text/x-vcard;application/x-cdlink;application/vnd.vsf;model/vrml;application/vnd.vcx;model/vnd.mts;model/vnd.vtu;application/vnd.visionary;video/vnd.vivo;applicatin/ccxml+xml,;application/voicexml+xml;application/x-wais-source;application/vnd.wap.wbxml;image/vnd.wap.wbmp;audio/x-wav;application/davmount+xml;application/x-font-woff;application/wspolicy+xml;image/webp;application/vnd.webturb;application/widget;application/winhlp;text/vnd.wap.wml;text/vnd.wap.wmlscript;application/vnd.wap.wmlscriptc;application/vnd.wordperfect;application/vnd.wt.stf;application/wsdl+xml;image/x-xbitmap;image/x-xpixmap;image/x-xwindowump;application/x-x509-ca-cert;application/x-xfig;application/xhtml+xml;application/xml;application/xcap-diff+xml;application/xenc+xml;application/patch-ops-error+xml;application/resource-lists+xml;application/rls-services+xml;aplication/resource-lists-diff+xml;application/xslt+xml;application/xop+xml;application/x-xpinstall;application/xspf+xml;application/vnd.mozilla.xul+xml;chemical/x-xyz;text/yaml;application/yang;application/yin+xml;application/vnd.ul;application/zip;application/vnd.handheld-entertainment+xml;application/vnd.zzazz.deck+xml");
	    ffOptions.setProfile(ffProfile);
	    break;
	case MICROSOFT_IE:
	    ieOptions = new InternetExplorerOptions();
	    ieOptions.setCapability("platform", getDesiredOperatingSystem());
	    ieOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
	    break;
	case GOOGLE_CHROME:
	    chOptions = new ChromeOptions();
	    chOptions.setCapability("platform", getDesiredOperatingSystem());
	    chOptions.addArguments("--no-sandbox");
	    chOptions.addArguments("--disable-infobars"); // disable automation info bar
	    if (HEADLESS_EXECUTION) {
		// https://developers.google.com/web/updates/2017/04/headless-chrome
		chOptions.addArguments("--headless");
		chOptions.addArguments("--disable-gpu"); // Temporarily needed if running on Windows
	    }
	    chOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
	    Map<String, Object> chromePreferences = new HashMap<>();
	    chromePreferences.put("profile.default_content_settings.popups", 0);
	    chromePreferences.put("download.prompt_for_download", "false");
	    chromePreferences.put("download.default_directory", downloadsFolderPath);
	    chOptions.setExperimentalOption("prefs", chromePreferences);

	    break;
	case MICROSOFT_EDGE:
	    edOptions = new EdgeOptions();
	    edOptions.setCapability("platform", getDesiredOperatingSystem());
	    edOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
	    break;
	case APPLE_SAFARI:
	    sfOptions = new SafariOptions();
	    sfOptions.setCapability("platform", getDesiredOperatingSystem());
	    sfOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);

	    sfOptions.setCapability("safari.options.dataDir", downloadsFolderPath);
	    break;
	default:
	    failAction("setDriverOptions", "Unsupported Browser Type [" + browserName + "].");
	    break;
	}
    }

    private static WebDriver createNewLocalDriverInstance(String browserName) {
	String initialLog = "Attempting to run locally on: [" + targetOperatingSystem + "], [" + browserName + "]";
	if (HEADLESS_EXECUTION) {
	    initialLog = initialLog + ", Headless Execution";
	}
	ReportManager.log(initialLog + ".");
	String browserInstanceID = browserName + "_" + System.currentTimeMillis();

	BrowserType browserType = getBrowserTypeFromName(browserName);

	switch (browserType) {
	case MOZILLA_FIREFOX:
	    if (!customDriverName.equals("") && !customDriverPath.equals("")) {
		System.setProperty("webdriver.gecko.driver",
			customDriverPath + customDriverName + setDriversExtecutableFileExtension());
	    } else {
		WebDriverManager.firefoxdriver().setup();
	    }
	    driver = new FirefoxDriver(ffOptions);
	    drivers.put(browserInstanceID, new HashMap<String, WebDriver>());
	    drivers.get(browserInstanceID).put(targetOperatingSystem, driver);
	    ReportManager.log("Successfully Opened Mozilla Firefox.");

	    break;
	case MICROSOFT_IE:
	    if (!customDriverName.equals("") && !customDriverPath.equals("")) {
		System.setProperty("webdriver.ie.driver",
			customDriverPath + customDriverName + setDriversExtecutableFileExtension());
	    } else {
		WebDriverManager.iedriver().setup();
	    }
	    driver = new InternetExplorerDriver(ieOptions);
	    drivers.put(browserInstanceID, new HashMap<String, WebDriver>());
	    drivers.get(browserInstanceID).put(targetOperatingSystem, driver);
	    ReportManager.log("Successfully Opened Microsoft Internet Explorer.");

	    break;
	case GOOGLE_CHROME:
	    if (!customDriverName.equals("") && !customDriverPath.equals("")) {
		System.setProperty("webdriver.chrome.driver",
			customDriverPath + customDriverName + setDriversExtecutableFileExtension());
	    } else {
		WebDriverManager.chromedriver().setup();
	    }
	    driver = new ChromeDriver(chOptions);
	    drivers.put(browserInstanceID, new HashMap<String, WebDriver>());
	    drivers.get(browserInstanceID).put(targetOperatingSystem, driver);
	    ReportManager.log("Successfully Opened Google Chrome.");
	    break;
	case MICROSOFT_EDGE:
	    if (!customDriverName.equals("") && !customDriverPath.equals("")) {
		System.setProperty("webdriver.edge.driver",
			customDriverPath + customDriverName + setDriversExtecutableFileExtension());
	    } else {
		WebDriverManager.edgedriver().setup();
	    }
	    driver = new EdgeDriver(edOptions);
	    drivers.put(browserInstanceID, new HashMap<String, WebDriver>());
	    drivers.get(browserInstanceID).put(targetOperatingSystem, driver);
	    ReportManager.log("Successfully Opened Microsoft Edge.");
	    break;
	case APPLE_SAFARI:
	    try {
		driver = new SafariDriver(sfOptions);
	    } catch (org.openqa.selenium.SessionNotCreatedException e) {
		ReportManager.log(e);
		failAction("createNewLocalDriverInstance", "Failed to create a session on" + browserType.toString());
	    }

	    drivers.put(browserInstanceID, new HashMap<String, WebDriver>());
	    drivers.get(browserInstanceID).put(targetOperatingSystem, driver);
	    ReportManager.log("Successfully Opened Safari.");
	    break;
	default:
	    failAction("createNewLocalDriverInstance", "Unsupported Browser Type [" + browserName + "].");
	    break;
	}
	return driver;
    }

    private static WebDriver createNewRemoteDriverInstance(String browserName) {
	String initialLog = "Attempting to run remotely on: [" + targetOperatingSystem + "], [" + browserName + "], ["
		+ TARGET_HUB_URL + "]";
	if (HEADLESS_EXECUTION) {
	    initialLog = initialLog + ", Headless Execution";
	}
	ReportManager.log(initialLog + ".");

	BrowserType browserType = getBrowserTypeFromName(browserName);

	try {
	    switch (browserType) {
	    case MOZILLA_FIREFOX:
		driver = new RemoteWebDriver(new URL(TARGET_HUB_URL), ffOptions);
		drivers.put(browserName, new HashMap<String, WebDriver>());
		drivers.get(browserName).put(targetOperatingSystem, driver);
		ReportManager.log("Successfully Opened Mozilla Firefox.");
		break;
	    case MICROSOFT_IE:
		driver = new RemoteWebDriver(new URL(TARGET_HUB_URL), ieOptions);
		drivers.put(browserName, new HashMap<String, WebDriver>());
		drivers.get(browserName).put(targetOperatingSystem, driver);
		ReportManager.log("Successfully Opened Microsoft Internet Explorer.");
		break;
	    case GOOGLE_CHROME:
		driver = new RemoteWebDriver(new URL(TARGET_HUB_URL), chOptions);
		drivers.put(browserName, new HashMap<String, WebDriver>());
		drivers.get(browserName).put(targetOperatingSystem, driver);
		ReportManager.log("Successfully Opened Google Chrome.");
		break;
	    case MICROSOFT_EDGE:
		driver = new RemoteWebDriver(new URL(TARGET_HUB_URL), edOptions);
		drivers.put(browserName, new HashMap<String, WebDriver>());
		drivers.get(browserName).put(targetOperatingSystem, driver);
		ReportManager.log("Successfully Opened Microsoft Edge.");
		break;
	    case APPLE_SAFARI:
		driver = new RemoteWebDriver(new URL(TARGET_HUB_URL), sfOptions);
		drivers.put(browserName, new HashMap<String, WebDriver>());
		drivers.get(browserName).put(targetOperatingSystem, driver);
		ReportManager.log("Successfully Opened Safari.");
		break;
	    default:
		failAction("createNewRemoteDriverInstance", "Unsupported Browser Type [" + browserName + "].");
		break;
	    }
	    ((RemoteWebDriver) driver).setFileDetector(new LocalFileDetector());
	} catch (UnreachableBrowserException e) {
	    killSwitch = true;
	    ReportManager.log(e);
	    failAction("createNewRemoteDriverInstance", "Unreachable Browser, terminated test suite execution.");
	} catch (WebDriverException e) {
	    ReportManager.log(e);
	    if (e.getMessage().contains("Error forwarding the new session cannot find")) {
		ReportManager.log("Failed to run remotely on: [" + targetOperatingSystem + "], [" + browserName + "], ["
			+ TARGET_HUB_URL + "].");
		failAction("createNewRemoteDriverInstance",
			"Error forwarding the new session: Couldn't find a node that matches the desired capabilities.");
	    } else {
		ReportManager.log("Failed to run remotely on: [" + targetOperatingSystem + "], [" + browserName + "], ["
			+ TARGET_HUB_URL + "].");
		failAction("createNewRemoteDriverInstance", "Unhandled Error.");
	    }
	} catch (MalformedURLException e) {
	    ReportManager.log(e);
	}
	return driver;
    }

    private static Platform getDesiredOperatingSystem() {
	OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);

	switch (operatingSystem) {
	case WINDOWS:
	    return Platform.WINDOWS;
	case LINUX:
	    return Platform.LINUX;
	case MACOS:
	    return Platform.MAC;
	default:
	    ReportManager.log(
		    "Unsupported Operating System [" + targetOperatingSystem + "], setting target platform to [ANY].");
	    return Platform.ANY;
	}
    }

    private static void attemptToCloseOrQuitBrowser(Entry<String, WebDriver> driverEntry, boolean quit) {
	try {
	    if (quit) {
		driverEntry.getValue().quit();
	    } else {
		driverEntry.getValue().close();
	    }
	} catch (NoSuchSessionException e) {
	    // browser was already closed by the .close() method
	} catch (Exception e) {
	    ReportManager.log(e);
	}

    }

    private static void attachBrowserLogs(String browserName, WebDriver driver) {
	if (!browserName.contains(BrowserType.MOZILLA_FIREFOX.getValue())) {
	    // The Selenium log API isn’t supported by geckodriver.
	    // Confirmed to work with chromeDriver

	    StringBuilder logBuilder;
	    String performanceLogText = "";
	    String driverLogText = "";

	    try {
		logBuilder = new StringBuilder();
		for (LogEntry entry : driver.manage().logs().get(LogType.PERFORMANCE)) {
		    logBuilder.append(entry.toString() + System.lineSeparator());
		}
		performanceLogText = logBuilder.toString();
		ReportManager.attach("Selenium WebDriver Logs", "Performance Logs for [" + browserName + "]",
			performanceLogText);
	    } catch (WebDriverException e) {
		// exception when the defined log type is not found
		ReportManager.log(e);
	    }

	    try {
		logBuilder = new StringBuilder();
		for (LogEntry entry : driver.manage().logs().get(LogType.DRIVER)) {
		    logBuilder.append(entry.toString() + System.lineSeparator());
		}
		driverLogText = logBuilder.toString();
		ReportManager.attach("Selenium WebDriver Logs", "Driver Logs for [" + browserName + "]", driverLogText);
	    } catch (WebDriverException e) {
		// exception when the defined log type is not found
		ReportManager.log(e);
	    }
	}
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [Public] Core Browser Factory Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Given that there is no test data file, read the target browser value from pom
     * configuration (overridable from jenkins), if "Default" it fails because it
     * will not be able to read the "Target Browser" cell value from the configured
     * test data file.
     * 
     * @return a singleton browser instance
     */
    public static WebDriver getBrowser() {
	return getBrowser(TARGET_BROWSER_NAME);
    }

    /**
     * Create and/or return an instance of the target browser (maintains a single
     * instance per browser type) and checks for cross-compatibility between the
     * selected browser and operating system
     * 
     * <p>
     * This method will be removed soon. Use
     * {@link BrowserFactory#getBrowser(BrowserType)} instead.
     * 
     * 
     * @param browserName the name of the browser that you want to run, currently
     *                    supports 'MozillaFirefox', 'MicrosoftInternetExplorer',
     *                    'GoogleChrome', and 'MicrosoftEdge'
     * @return a singleton browser instance
     */
    public static WebDriver getBrowser(String browserName) {
	try {
	    checkBrowserOSCrossCompatibility(browserName);
	    // check cross-compatibility between the selected operating system and browser
	    // and report in case they are not compatible
	    setLoggingPrefrences();
	    // set logging global preferences
	    setDriverOptions(browserName);
	    // set driver options with respect to the target browser name

	    if (BROWSEROBJECTSINGLETON) {
		closeAllDrivers();
	    }

	    if (EXECUTION_ADDRESS.equals("local")) {
		// Manage local execution
		driver = createNewLocalDriverInstance(browserName);
	    } else {
		// Manage remote execution
		driver = createNewRemoteDriverInstance(browserName);
	    }
	    driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT, TimeUnit.SECONDS);
	    if (WAIT_IMPLICITLY) {
		driver.manage().timeouts().implicitlyWait(IMPLICIT_WAIT_TIMEOUT, TimeUnit.SECONDS);
	    }

	    JSWaiter.setDriver(driver);
	    if (AUTO_MAXIMIZE) {
		BrowserActions.maximizeWindow(driver); // Automatically maximize driver window after opening it
	    }
	    startAnimatedGif();
	} catch (NullPointerException e) {
	    ReportManager.log(e);
	    ReportManager.log("Unhandled Exception with Browser Type [" + browserName + "].");
	    Assert.fail("Unhandled Exception with Browser Type [" + browserName + "].");
	}
	return driver;
    }

    public static WebDriver getBrowser(BrowserType browserType) {
	return getBrowser(browserType.getValue());
    }

    /**
     * Close all open browser instances.
     * 
     */
    public static void closeAllDrivers() {
	if (!drivers.entrySet().isEmpty()) {
	    for (Entry<String, Map<String, WebDriver>> entry : drivers.entrySet()) {
		for (Entry<String, WebDriver> driverEntry : entry.getValue().entrySet()) {
		    attemptToCloseOrQuitBrowser(driverEntry, false);
		    attemptToCloseOrQuitBrowser(driverEntry, true);
		}
	    }
	    driver = null;
	    drivers.clear();
	    ReportManager.log("Successfully Closed All Browsers.");
	}
    }

    public static void attachBrowserLogs() {
	if (!drivers.entrySet().isEmpty()) {
	    try {
		for (Entry<String, Map<String, WebDriver>> entry : drivers.entrySet()) {
		    for (Entry<String, WebDriver> driverEntry : entry.getValue().entrySet()) {
			attachBrowserLogs(entry.getKey(), driverEntry.getValue());
		    }
		}
	    } catch (Exception e) {
		ReportManager.log(e);
	    }
	}
    }

    public static Boolean isBrowsersListEmpty() {
	return drivers.entrySet().isEmpty();
    }

    public static void startAnimatedGif() {
	if (CREATE_GIF && (driver != null)) {
	    ScreenshotManager.startAnimatedGif(driver);
	}
    }

    public static void attachAnimatedGif() {
	if (CREATE_GIF && (driver != null)) {
	    ScreenshotManager.attachAnimatedGif();
	}
    }

    public static int getActiveDriverSessions() {
	return drivers.entrySet().size();
    }

    public static boolean isKillSwitch() {
	return killSwitch;
    }

}
