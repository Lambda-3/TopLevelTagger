package org.lambda3.tagger;

/*
 * ==========================License-Start=============================
 * Top Level Tagger
 *
 * Copyright © 2017 Lambda³
 *
 * GNU General Public License 3
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 * ==========================License-End==============================
 */


import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Main {

    public static void main(String args[]) throws IOException {

        //Parse options
        ArgumentParser parser = ArgumentParsers.newArgumentParser("TopLevelTagger");
        parser.addArgument("-wnpath").help("path to WordNet database.").required(true);
        parser.addArgument("-inputfile").help("data file, one sentence per line").setDefault("System.in");
        parser.addArgument("-outputfile").help("result file, one pair <segment: label> per line").setDefault("System.out");
        parser.addArgument("-tagset").choices("tc", "fc").help("tc: top class (default) | fc: foundational class)").setDefault("tc");

        Namespace options;

        try {
            options = parser.parseArgs(args);
            String inputfile = options.get("inputfile").toString(); // data file, one sentence per line
            String outputfile = options.get("outputfile").toString(); // result file, one pair <segment: label> per line
            String wnpath = options.get("wnpath").toString(); // path of WordNet database files
            String tagset = options.get("tagset").toString(); // tc: top class (default) | fc: foundational class)

            TopLevelTagger tlt = new TopLevelTagger(wnpath);

            List<String> sentences = new ArrayList<>();
            List<List<WordMapping>> taggedSents;
            List<String> records = new ArrayList<>();

            if (inputfile.equals("System.in")) {
                //Read from standard input, until an empty line is entered
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                String line;
                while ((line = in.readLine()) != null && line.length() != 0) {
                    sentences.add(line);
                }
            } else {
                //Read input text from file
                sentences = Files.lines(Paths.get(inputfile)).collect(Collectors.toList());
            }

            if (sentences.size() > 0) {
                //Tag sentences
                if (tagset.equals("fc")) {
                    taggedSents = tlt.tagWithFoundationClass(sentences, true);
                } else {
                    taggedSents = tlt.tagWithTopClass(sentences, true);
                }

                for (List<WordMapping> taggedSent : taggedSents) {
                    for (WordMapping mapping : taggedSent) {
                        records.add(mapping.getWord() + ": " + mapping.getLabel() + "\n");
                    }
                    records.add("\n");
                }

                if (!outputfile.equals("System.out")) {
                    //Write results to file
                    try {
                        FileWriter writer = new FileWriter(outputfile);
                        for (String record : records) {
                            writer.write(record);
                        }
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    //Write results to standard output
                    System.out.println();
                    records.forEach(System.out::print);
                }
            }
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

}
