This directory contains source code for parsing clinical trials from
ClinicalTrials.gov. To build a self-contained `clinical.jar` file,
you'll need at least Java 1.5 and `ant`.


```
ant dist
```

This should create a self-executable jar file `clinical.jar` in the
`dist` directory. To match against the latest clinical trials, simply
type the following

```
java -jar dist/clinical.jar dictionary.txt
```

where `dictionary.txt` is a dictionary to match against. See the file
`data/dictionary.tsv` for inspiration.

Feel free to contact me at `nguyenda@mail.nih.gov` should you have any
problems.
