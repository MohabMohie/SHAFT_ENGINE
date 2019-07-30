package com.shaft.tools.listeners;

import java.util.List;

import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;

import com.shaft.tools.io.LogsReporter;

public class AlterSuiteListener implements IAlterSuiteListener {

    @Override
    public void alter(List<XmlSuite> suites) {
	// disable log4j housed inside some transitive dependencies
	org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);

	renameDefaultSuiteAndTest(suites);
	addLogsReporterToFirstTest(suites);
    }

    private void renameDefaultSuiteAndTest(List<XmlSuite> suites) {
	String prefix = "SHAFT_Engine: ";
	// rename default suite and test
	suites.forEach(suite -> {
	    if (suite.getName().toLowerCase().trim().equals("default suite")
		    || suite.getName().toLowerCase().trim().equals("surefire suite")) {
		suite.setName(prefix + "Custom Suite");
	    } else {
		suite.setName(prefix + suite.getName());
	    }
	    suite.getTests().forEach(test -> {
		if (test.getName().toLowerCase().trim().equals("default test")
			|| test.getName().toLowerCase().trim().equals("surefire test")) {
		    test.setName(prefix + "Custom Test");
		} else {
		    test.setName(prefix + test.getName());
		}
	    });
	});
    }

    private void addLogsReporterToFirstTest(List<XmlSuite> suites) {
	// alter first test and add the afterSuiteMethod
	XmlClass logsReporter = new XmlClass(LogsReporter.class.getName());
	suites.get(0).getTests().get(0).getClasses().add(logsReporter);
    }
}