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

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;
import edu.mit.jwi.morph.WordnetStemmer;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public final class TopLevelTagger {

    private static final List<String> validPOS = Arrays.asList("NN", "NNS", "NNP", "NNPS", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "VBT", "FW");
    private static final List<String> verbForm = Arrays.asList("VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "VBT");

    private IDictionary dict;

    public TopLevelTagger(String wnpath) throws IOException {
        dict = new Dictionary(new File(wnpath));
        dict.open();
    }

    //Load predefined top class sets
    private List<String> loadSuperHypernyms(String filename) {
        try (InputStreamReader is = new InputStreamReader(ClassLoader.getSystemResourceAsStream(filename))) {
            return new BufferedReader(is).lines().collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Load the standard mappings between words or synsets and (DOLCE or VerbNet) classes
    private Map<String, String> loadMappings(String filename) {

        Map<String, String> mappings = new HashMap<>();

        InputStream input = ClassLoader.getSystemResourceAsStream(filename);
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        try {
            String line = null;

            while ((line = br.readLine()) != null) {
                String key = line.split(";")[0];

                if (!mappings.containsKey(key)) {
                    String value = line.split(";")[1];
                    mappings.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mappings;
    }

    //Remove the words at the end of a sentence
    private String removeLastWords(String text, int numWords) {

        String newText = text;

        if (text.contains("_")) {
            for (int i = 0; i < numWords; i++) {
                newText = newText.contains("_") ? newText.substring(0, newText.lastIndexOf("_")) : "";
            }
        } else {
            newText = "";
        }

        return newText;
    }

    //Split each sentence (in a list of sentences) into phrases, being each phrase the longest entry found in WordNet
    private List<List<String>> split(List<String> sentences, boolean verbose) throws IOException {

        if (verbose) {
            System.out.println("Splitting sentences...");
        }

        List<List<String>> chunksLists = new ArrayList<>();

        //Word stemmer
        WordnetStemmer stemmer = new WordnetStemmer(dict);

        //POS tagger
        RedwoodConfiguration.empty().capture(System.err).apply();
        MaxentTagger tagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger");
        RedwoodConfiguration.current().clear().apply();

        for (String text : sentences) {
            List<String> chunks = new ArrayList<String>();
            text = text.replaceAll("''", "\"").replaceAll("[\\W&&[^-']]", " ").replaceAll("[\\s]+", " ").trim(); //Replace all non-alphanumerics but dashes and single apostrophes by blanks

            String entry = text.replaceAll(" ", "_");

            String currentEntry = entry;
            IWord word;
            String synsetID;
            String chunk;

            //Scans the sentence from left to right. Initially, the whole sentence is considered an entry;
            //if it is not found in WN, the leftmost word is recursively removed until a valid entry is identified
            while (entry.length() > 0) {
                while (entry.length() >= 1) {
                    boolean skip = false;
                    boolean isVerbForm = false;
                    POS pos = POS.NOUN;
                    String newEntry = entry;

                    List<String> wordStems = stemmer.findStems(entry, pos);

                    //Get the word/phrase stem
                    if (wordStems.size() > 0) {
                        newEntry = wordStems.get(0);
                    }

                    if (!entry.contains("_")) { //a single word
                        //Get the POS tag
                        String tagged = tagger.tagString(entry);
                        String pt = tagged.substring(tagged.indexOf('_') + 1, tagged.length()).trim();

                        if (!validPOS.contains(pt)) { //not a noun, verb, adjective or adverb
                            chunk = entry + ";00000000;null";
                            chunks.add(chunk);
                            entry = removeLastWords(currentEntry, 1);
                            currentEntry = entry;
                            skip = true;
                            break;
                        } else {
                            if (verbForm.contains(pt)) { //ensure that words that are both a noun and a verb will be correctly located if the POS tagger has already classified them as verbs
                                pos = POS.VERB;
                                wordStems = stemmer.findStems(entry, pos);

                                //Get the verb stem
                                if (wordStems.size() > 0) {
                                    newEntry = wordStems.get(0);
                                }

                                isVerbForm = true;
                            }
                        }
                    }

                    if (!skip) {
                        if (isVerbForm) { //single-word verbs
                            IIndexWord words = dict.getIndexWord(newEntry, pos);
                            try {
                                word = dict.getWord(words.getWordIDs().get(0));
                                synsetID = word.getSynset().getID().toString();
                                chunk = entry.replaceAll("_", " ") + ";" + synsetID + ";verb";
                                chunks.add(chunk);
                                entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
                                currentEntry = entry;
                                break;
                            } catch (NullPointerException npen) { //verb not in WordNet
                                chunk = entry + ";00000000;null";
                                chunks.add(chunk);
                                entry = removeLastWords(currentEntry, 1);
                                currentEntry = entry;
                                break;
                            }
                        } else { //single-word nouns, adjectives and adverbs, and all multiple-words expressions
                            IIndexWord nouns = dict.getIndexWord(newEntry, POS.NOUN);
                            try {
                                word = dict.getWord(nouns.getWordIDs().get(0));
                                synsetID = word.getSynset().getID().toString();
                                chunk = entry.replaceAll("_", " ") + ";" + synsetID + ";noun";
                                chunks.add(chunk);
                                entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
                                currentEntry = entry;
                                break;
                            } catch (NullPointerException npen) {
                                IIndexWord verbs = dict.getIndexWord(newEntry, POS.VERB);
                                try {
                                    word = dict.getWord(verbs.getWordIDs().get(0));
                                    synsetID = word.getSynset().getID().toString();
                                    chunk = entry.replaceAll("_", " ") + ";" + synsetID + ";verb";
                                    chunks.add(chunk);
                                    entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
                                    currentEntry = entry;
                                    break;
                                } catch (NullPointerException npev) {
                                    IIndexWord adjs = dict.getIndexWord(newEntry, POS.ADJECTIVE);
                                    try {
                                        word = dict.getWord(adjs.getWordIDs().get(0));
                                        synsetID = word.getSynset().getID().toString();
                                        chunk = entry.replaceAll("_", " ") + ";" + synsetID + ";null";
                                        chunks.add(chunk);
                                        entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
                                        currentEntry = entry;
                                        break;
                                    } catch (NullPointerException npea) {
                                        IIndexWord advs = dict.getIndexWord(newEntry, POS.ADVERB);
                                        try {
                                            word = dict.getWord(advs.getWordIDs().get(0));
                                            synsetID = word.getSynset().getID().toString();
                                            chunk = entry.replaceAll("_", " ") + ";" + synsetID + ";null";
                                            chunks.add(chunk);
                                            entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
                                            currentEntry = entry;
                                            break;
                                        } catch (NullPointerException nper) { // word not found in any grammatical class
                                            if (entry.contains("_")) {
                                                entry = entry.substring(entry.indexOf("_") + 1, entry.length());
                                            } else {
                                                chunk = entry.replaceAll("_", " ") + ";00000000;null";
                                                chunks.add(chunk);
                                                entry = removeLastWords(currentEntry, 1);
                                                currentEntry = entry;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            chunksLists.add(chunks);
        }
        return chunksLists;
    }

    //Check if the synset has hypernyms
    private boolean hasHypernyms(ISynset synset) {

        boolean hasHypernyms = false;
        List<ISynsetID> hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM);
        List<ISynsetID> hypernymInsts = synset.getRelatedSynsets(Pointer.HYPERNYM_INSTANCE);

        if (hypernyms.size() > 0 || hypernymInsts.size() > 0)
            hasHypernyms = true;

        return hasHypernyms;
    }

    //Assign a tag to each word/phrase in each sentence representing their top class
    public List<List<WordMapping>> tagWithTopClass(List<String> sentences, boolean verbose) {

        List<String> firstLevelTC = loadSuperHypernyms("1stlevelTC.txt");
        List<String> secondLevelTC = loadSuperHypernyms("2ndlevelTC.txt");
        Map<String, String> VNMapping = loadMappings("VNMapping.txt");

        List<List<WordMapping>> mappingsLists = new ArrayList<List<WordMapping>>();

        IIndexWord idxWord;
        IWordID wordID;
        IWord word;
        ISynset synset;

        WordnetStemmer stemmer = new WordnetStemmer(dict);
        List<String> wordStems;

        List<List<String>> wordsLists = new ArrayList<>();

        try {
            wordsLists = split(sentences, verbose);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (verbose) {
            System.out.println("Retrieving top classes...");
        }

        for (List<String> words : wordsLists) {
            List<WordMapping> mappings = new ArrayList<>();
            for (int i = words.size() - 1; i >= 0; i--) {
                String wordEntry = words.get(i);
                String chunk = wordEntry.split(";")[0];
                String pt = wordEntry.split(";")[2];

                String entry = chunk;

                if (pt.equals("null")) {
                    mappings.add(new WordMapping(chunk, "O"));
                } else { //only nouns and verbs have top classes
                    if (pt.equals("noun")) {
                        //Search for a WN hypernym in the 1st level set, if not found, search in the 2nd level set
                        String superHyp = "";
                        wordStems = stemmer.findStems(chunk, POS.NOUN);

                        if (wordStems.size() > 0) {
                            entry = wordStems.get(0);
                        }
                        idxWord = dict.getIndexWord(entry, POS.NOUN);

                        try {
                            wordID = idxWord.getWordIDs().get(0);
                            word = dict.getWord(wordID);
                            synset = word.getSynset();

                            if (hasHypernyms(synset)) {
                                if (firstLevelTC.contains(synset.getID().toString()) || secondLevelTC.contains(synset.getID().toString())) {
                                    superHyp = synset.getWord(1).getLemma();
                                } else {
                                    ISynset lastSynset = null;

                                    while (hasHypernyms(synset)) {
                                        List<ISynsetID> hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM);

                                        if (hypernyms.size() == 0) {
                                            hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM_INSTANCE);
                                        }

                                        ISynsetID hypernym = hypernyms.get(0);

                                        if (dict.getSynset(hypernym).equals(lastSynset)) {
                                            //Stop at erroneous circular references between synsets present in WN 3.0
                                            break;
                                        } else {
                                            lastSynset = synset;
                                            superHyp = dict.getSynset(hypernym).getWord(1).getLemma();
                                        }

                                        if (firstLevelTC.contains(hypernym.toString()) || secondLevelTC.contains(hypernym.toString())) {
                                            break;
                                        } else {
                                            synset = dict.getSynset(hypernym);
                                        }
                                    }
                                }
                            } else {
                                superHyp = chunk.replaceAll(" ", "_");
                            }
                        } catch (NullPointerException e) { //noun not found in WordNet
                            superHyp = "O";
                        }

                        mappings.add(new WordMapping(chunk, superHyp));
                    } else {
                        if (pt.equals("verb")) {
                            //Search for a VerbNet class, if not found, search for a hypernym's VN class, if also
                            //not found, assign the highest level WN hypernym as top class
                            String VNClass = "";
                            wordStems = stemmer.findStems(chunk, POS.VERB);

                            if (wordStems.size() > 0) {
                                entry = wordStems.get(0);
                            }
                            idxWord = dict.getIndexWord(entry, POS.VERB);

                            try {
                                wordID = idxWord.getWordIDs().get(0);
                                word = dict.getWord(wordID);
                                synset = word.getSynset();


                                List<IWord> synWords = synset.getWords();

                                for (IWord synWord : synWords) {
                                    String senseKey = synWord.getSenseKey().toString();
                                    senseKey = senseKey.substring(0, senseKey.length() - 2);

                                    if (VNMapping.containsKey(senseKey)) {
                                        VNClass = VNMapping.get(senseKey);
                                        break;
                                    }
                                }

                                if (VNClass.length() == 0) {
                                    if (hasHypernyms(synset)) {
                                        ISynset lastSynset = null;
                                        boolean found = false;

                                        while (hasHypernyms(synset)) {
                                            ISynsetID hypernym = synset.getRelatedSynsets(Pointer.HYPERNYM).get(0);

                                            if (dict.getSynset(hypernym).equals(lastSynset)) {
                                                //Stop at erroneous circular references between synsets present in WN 3.0
                                                break;
                                            } else {
                                                lastSynset = synset;
                                                synset = dict.getSynset(hypernym);
                                                synWords = synset.getWords();

                                                for (IWord synWord : synWords) {
                                                    String senseKey = synWord.getSenseKey().toString();
                                                    senseKey = senseKey.substring(0, senseKey.length() - 2);

                                                    if (VNMapping.containsKey(senseKey)) {
                                                        VNClass = VNMapping.get(senseKey);
                                                        found = true;
                                                        break;
                                                    }
                                                }

                                                if (found) {
                                                    break;
                                                }
                                            }
                                        }

                                        if (VNClass.length() == 0) {
                                            VNClass = synset.getWord(1).getLemma();
                                        }

                                    } else {
                                        VNClass = synset.getWord(1).getLemma();
                                    }
                                }
                            } catch (NullPointerException e) { //verb not found in WordNet
                                VNClass = "O";
                            }

                            mappings.add(new WordMapping(chunk, VNClass));
                        }
                    }
                }
            }
            mappingsLists.add(mappings);
        }
        return mappingsLists;
    }

    //Assign a tag to each word/phrase in each sentence representing their foundational class (from DOLCE foundational ontology)
    public List<List<WordMapping>> tagWithFoundationClass(List<String> sentences, boolean verbose) {

        List<List<WordMapping>> mappingsLists = new ArrayList<>();
        Map<String, String> FOMapping = loadMappings("FOMapping.txt");
        List<List<String>> wordsLists = new ArrayList<>();

        try {
            wordsLists = split(sentences, verbose);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (verbose) {
            System.out.println("Retrieving foundational classes...");
        }

        for (List<String> words : wordsLists) {
            List<WordMapping> mappings = new ArrayList<>();
            for (int i = words.size() - 1; i >= 0; i--) {
                String entry = words.get(i);
                String word = entry.split(";")[0];
                String label = FOMapping.containsKey(entry.split(";")[1]) ? FOMapping.get(entry.split(";")[1]) : "O";
                mappings.add(new WordMapping(word, label));
            }
            mappingsLists.add(mappings);
        }
        return mappingsLists;
    }

}
