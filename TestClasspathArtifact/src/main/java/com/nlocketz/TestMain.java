package com.nlocketz;

import org.apache.commons.cli.*;

public class TestMain {
    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("example", false, "stupid test example.");
        CommandLineParser parser = new DefaultParser();
        CommandLine cl = parser.parse(options,args);
        System.out.println(Boolean.toString(cl.hasOption("example")));
    }
}
