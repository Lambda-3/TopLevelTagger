# Top Level Tagger

Top Level Tagger is a semantic annotation tool that assigns, for each word or phrase in a piece of text, a label representing their high level taxonomic category. It works with 
two types of categories: *top classes* and *foundational classes*. Top classes are composed by WordNet hypernyms (for nouns) and VerbNet classes (for verbs). Foundational classes 
are even more abstract categories, situated at the highest level in a taxonomy of concepts, above top classes. The Top Level Tagger uses the classes from the DOLCE upper level 
ontology as foundational classes, applying the mapping described in the following paper:

> Vivian S. Silva, André Freitas and Siegfried Handschuh. Word Tagging with Foundational Ontology Classes: Extending the WordNet-DOLCE Mapping to Verbs. 20th International 
Conference on Knowledge Engineering and Knowledge Management (EKAW), Bologna. 2016.

Rather than tagging each word individually, the Top Level Tagger identify multiple-word expressions and assigns a single label to the whole phrase. It relies on WordNet 3.0 
to correctly split the sentences into phrases and to find the most suitable category for each word or phrase based on their taxonomic relationships.

# Usage

## At command line

```
java -jar tlt.jar -wnpath=path-to-wordnet-database [-inputfile=path-to-input-file] [-outputfile=path-to-output-file] [-tagset=tc|fc]
```

where:

- wnpath: *Required*. Path to the "dict" folder of the WordNet 3.0 installation. WordNet can be downloaded [here](http://wordnet.princeton.edu/wordnet/download/current-version/). Since the mapping to the DOLCE foundational ontology 
was performed over version 3.0, the tagger may not work properly with a different version.

- inputfile: *Optional*. Path to the file containing the input text, one sentence per line. If omitted, reads the sentences from the standard input, until a blank line 
is entered.

- outputfile: *Optional*. Path to the file where the output will be saved. If omitted, the result is written to the standard output.

- tagset: *Optional*. The tagset to be used, "tc" for top classes (default) and "fc" for foundational classes. If omitted, the default "tc" is assumed.

## As a library

An example of how to call the Top Level Tagger from code:

```java
String wnpath = "C:\\Program Files\\WordNet\\3.0\\dict";
try{
	TopLevelTagger tlt = new TopLevelTagger(wnpath);
	List<String> sentences = Arrays.asList("The Popular Front for the Liberation of Palestine was set up in 1967.",
		"The PFLP gained notoriety in the late 1960s and early 1970s for a series of armed attacks.",
		"It is described as a terrorist organization by the United States, Canada, Australia, and the European Union.");
		
	//Tag with top classes. Use the method tagWithFoundationClass to tag with DOLCE categories instead
	List<List<WordMapping>> taggedSents = tlt.tagWithTopClass(sentences);
			
	//List the results
	for (List<WordMapping> sentence : taggedSents){
		for (WordMapping word : sentence){
			System.out.println(word.getWord() + ": " + word.getLabel());
		}
		System.out.println();
	}
}	
catch (IOException e){
	e.printStackTrace();
}
```

The above example yields the following output, where "O" stands for the null label:

```
The: O
Popular Front for the Liberation of Palestine: group
was: O
set up: establish
in: O
1967: O

The: O
PFLP: group
gained: get
notoriety: state
in: O
the: O
late: O
1960s: time_period
and: O
early: O
1970s: time_period
for: O
a: O
series: group
of: O
armed: equip
attacks: event

It: O
is: O
described: characterize
as: O
a: O
terrorist organization: group
by: O
the: O
United States: location
Canada: location
Australia: location
and: O
the: O
European Union: group
```

# Citing

If you use the Top Level Tagger in your project, please cite the following paper:

```
@Inbook{Silva2016,
author="Silva, Vivian S.
and Freitas, Andr{\'e}
and Handschuh, Siegfried",
editor="Blomqvist, Eva
and Ciancarini, Paolo
and Poggi, Francesco
and Vitali, Fabio",
title="Word Tagging with Foundational Ontology Classes: Extending the WordNet-DOLCE Mapping to Verbs",
bookTitle="Knowledge Engineering and Knowledge Management: 20th International Conference, EKAW 2016, Bologna, Italy, November 19-23, 2016, Proceedings",
year="2016",
publisher="Springer International Publishing",
address="Cham",
pages="593--605",
isbn="978-3-319-49004-5",
doi="10.1007/978-3-319-49004-5_38",
url="http://dx.doi.org/10.1007/978-3-319-49004-5_38"
}
```