package tripod.clinical;

import java.util.*;
import java.io.*;
import java.util.zip.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.parsers.*;
import org.xml.sax.helpers.*;
import org.xml.sax.*;
import static tripod.clinical.SmithWaterman.*;

public class ClinicalCrawler 
    extends DefaultHandler implements java.net.ContentHandlerFactory {
    static final Logger logger = Logger.getLogger
        (ClinicalCrawler.class.getName());

    static final String MODIFIER_RESOURCE = 
        "resources/ClinicalCommonTokens.txt";

    /*
      a: Condition
      b: Intervention
      c: sponsor
      d: gender
      e: age group
      f: phase
      g: number enrolled
      h: funded by
      i: Study type
      j: study design
      k: NCT id
      l: other Ids
      m: first received date
      n: start date
      o: completion date
      p: last updated date
      q: last verified date
      r: acronym
      s: primary completion date
      t: outcome measure
      u: results first received

      http://clinicaltrials.gov/ct2/results/download?down_typ=fields&down_fmt=xml&down_stds=all&down_flds=shown&flds=a,b,c,e,l,m,n,o,p
    */
      
    static final String DOWNLOAD_URL = "http://clinicaltrials.gov/ct2/results/download?down_typ=fields&down_fmt=xml&down_stds=all&down_flds=shown&flds=a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t";
    
    static class Study {
        public String id;
	public String title;
	public List<String> conditions = new ArrayList<String>();
	public List<String> interventions = new ArrayList<String>();
	public List<String> sponsors = new ArrayList<String>();
	public List<String> phases = new ArrayList<String>();
	public String recieved;
	public String start;
	public String completion;
	public String updated;
	public String verified;
	public String url;

	public String toString (List<String> v) {
	    StringBuffer sb = new StringBuffer ();
	    if (!v.isEmpty()) {
		sb.append(v.get(0));
		for (int i = 1; i < v.size(); ++i) {
		    sb.append("\n" + v.get(i));
		}
	    }
	    return sb.toString();
	}
	public String getCondition () { return toString (conditions); }
	public String getSponsor () { return toString (sponsors); }
	public String getPhase () { return toString (phases); }
	public String getIntervention () { return toString (interventions); }
    }

    static final Study DONE = new Study ();

    class AlignWorker implements Runnable {
        BlockingQueue<Study> workQ;

        AlignWorker (BlockingQueue<Study> workQ) {
            this.workQ = workQ;
        }

        public void run () {
            Thread t = Thread.currentThread();
            String name = t.getName();
            logger.info(">> "+name+" started...");
            try {
                for (Study s; (s = workQ.take()) != DONE 
                         && !t.isInterrupted(); ) {
                    Set<AlignmentRef> matches = align (s);
                    output (s, matches);
                }
                logger.info("## "+name+" finishes!");
            }
            catch (InterruptedException ex) {
                logger.info("## "+name+" interrupted!");
            }
        }
    }

    static class AlignmentRef implements Comparable<AlignmentRef> {
        String id;
        String term;
        String ref;
        Alignment result;

        AlignmentRef (String id, String term, String ref, Alignment result) {
            this.id = id;
            this.term = term;
            this.ref = ref;
            this.result = result;
        }

        public boolean equals (Object obj) {
            if (this == obj) return true;
            if (obj instanceof AlignmentRef) {
                AlignmentRef ar = (AlignmentRef)obj;
                return compareTo (ar) == 0 && id.equals(ar.id);
            }
            return false;
        }

        public int compareTo (AlignmentRef ar) {
            int d = result.compareTo(ar.result);
            if (d == 0)
                d = id.compareTo(ar.id);
            return d;
        }
        //public int hashCode () { return id.hashCode(); }

        public void print (PrintStream ps) {
            ps.println(id);
            ps.println("++++ \""+term+"\"");
            ps.println("---- \""+ref+"\"");
            ps.println(result);
            ps.println("["+String.format("%1$.3f,", result.global())
                       +String.format("%1$.3f,", result.local())
                       +String.format("%1$.3f]", result.similarity()));
        }
    }

    static class AlignmentResults {
        String term;
        boolean hasExact = false;
        Set<AlignmentRef> results = new TreeSet<AlignmentRef>();

        AlignmentResults (String term) {
            this.term = term;
        }
        
        public boolean add (AlignmentRef ref) {
            if (hasExact) 
                return false;

            double sim = ref.result.similarity();
            if (sim < 1.) {
            }
            else {
                // remove all entries since we have exact match
                hasExact = true;
                results.clear();
            }
            results.add(ref);

            return true;
        }

        public boolean hasExact () { return hasExact; }
        public Set<AlignmentRef> results () { return results; }
        public int size () { return results.size(); }

        public void print (PrintStream ps) {
            ps.println("++++ \""+term+"\"");
            int i = 1;
            for (AlignmentRef ar : results) {
                Alignment aln = ar.result;
                ps.println("-+- "+i+" -+-");
                ps.println("["+ar.id+"]");
                ps.println(aln);
                ps.println("["+String.format("%1$.3f,", aln.global())
                           +String.format("%1$.3f,", aln.local())
                           +String.format("%1$.3f]", aln.similarity()));
                ++i;
            }
        }
    }

    /* 
     * transient parsing variables
     */
    Study study;
    boolean isDrug = false;
    StringBuffer content = new StringBuffer ();
    List<Study> studies = new ArrayList<Study>();

    static class ClinicalContentHandler extends java.net.ContentHandler {
	public ClinicalContentHandler () {
	}

	// return a temp file name...
	public Object getContent (URLConnection con) throws IOException {
	    InputStream is = con.getInputStream();
	    File file = File.createTempFile("zzz", ".zip");
	    BufferedOutputStream bos = new BufferedOutputStream 
		(new FileOutputStream (file));
	    byte[] buf = new byte[4096];
	    int total = con.getContentLength();
	    int size = 0;
	    for (int nb; (nb = is.read(buf, 0, buf.length)) > 0; ) {
		size += nb;
		bos.write(buf, 0, nb);
                if (size %1024== 0) {
                    System.err.printf(".");
                    System.err.flush();
                }
	    }
	    System.err.println();
	    bos.close();
	    return file;
	}
    }

    protected ConcurrentMap<String, Double> modifiers = 
        new ConcurrentHashMap<String, Double>();
    protected ConcurrentMap<String, Set<String>> dictionary = 
        new ConcurrentHashMap<String, Set<String>>();
    protected BlockingQueue<Study> queue = 
        new ArrayBlockingQueue<Study>(1000);

    // term to AlignmentResults
    protected ConcurrentMap<String, AlignmentResults> alignments = 
        new ConcurrentHashMap<String, AlignmentResults>();

    protected PrintStream matchStream = System.out;
    protected PrintStream alignStream = null;
    protected int maxCandidates = 5;

    protected ExecutorService threadPool;

    public ClinicalCrawler () {
        this (1);
    }

    public ClinicalCrawler (int threads) {
        threads = Math.max(1, threads);
        threadPool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; ++i) {
            threadPool.submit(new AlignWorker (queue));
        }
        URLConnection.setContentHandlerFactory(this);
    }

    public int loadModifiers () throws IOException {
        modifiers.clear();
        return loadModifiers (ClinicalCrawler.class
                              .getResourceAsStream(MODIFIER_RESOURCE));
    }

    public void setMatchStream (PrintStream matchStream) {
        if (matchStream != null)
            matchStream.println("CT_ID,MATCH_TERM,DICT_ID,DICT_TERM"
                                +",SCORE,GLOBAL,LOCAL");
        this.matchStream = matchStream;
    }
    public PrintStream getMatchStream () { return matchStream; }

    public void setAlignStream (PrintStream alignStream) {
        this.alignStream = alignStream;
    }
    public PrintStream getAlignStream () { return alignStream; }

    public int loadModifiers (InputStream is) throws IOException {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        int lines = 1;
        for (String line; (line = br.readLine()) != null; ++lines) {
            if (line.charAt(0) == '#')
                continue; // ignore comments

            String[] tokens = line.split("[\\s]+");
            if (tokens.length == 2) {
                try {
                    modifiers.put(tokens[0].trim(), 
                                  Double.parseDouble(tokens[1]));
                }
                catch (NumberFormatException ex) {
                    logger.warning("Line "+lines+": Ignore \""+tokens[0]
                                   +"\" because of bogus weight: "+tokens[1]);
                }
            }
            else {
                //logger.warning("Line "+lines+": "+line);
                modifiers.put(line.trim(), 0.);
            }
        }

        logger.info("## "+modifiers.size() + " modifiers loaded!");
        return modifiers.size();
    }

    /**
     * tab delimited dictionary entries with terms in the first column
     * and equivalence class in the second
     */
    public int loadDict (InputStream is) throws IOException {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        int lines = 1;

        Comparator<String> order = new Comparator<String>() {
            public int compare (String s1, String s2) {
                int d = s1.length() - s2.length();
                if (d == 0) {
                    d = s1.compareTo(s2);
                }
                return d;
            }
        };
        for (String line; (line = br.readLine()) != null; ++lines) {
            String[] tokens = line.split("[\t]+");
            if (tokens.length == 2) {
                String clz = tokens[1].trim();
                Set<String> terms = dictionary.get(clz);
                if (terms == null) {
                    dictionary.put
                        (clz, terms = new TreeSet<String>());
                }
                terms.add(tokens[0].trim());
            }
            else {
                logger.warning("** Skip line "+lines+": "+line);
            }
        }

        logger.info("Dictionary loaded..."+dictionary.size());
        return dictionary.size();
    }

    public java.net.ContentHandler createContentHandler (String mimetype) {
        if (mimetype.equals("application/zip")) {
            return new ClinicalContentHandler ();
        }
        return null;
    }

    public File download () throws Exception {
        URL url = new URL (DOWNLOAD_URL);
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        int timeout = 1000000000;
        con.setConnectTimeout(timeout);
        con.setReadTimeout(timeout);

        logger.info("## Downloading clinical trials...");
        return (File)con.getContent();
    }

    public void parseCT (File file) throws Exception {
	ZipFile zf = new ZipFile (file);
	for (Enumeration e = zf.entries(); e.hasMoreElements();) {
	    ZipEntry zip = (ZipEntry)e.nextElement();
	    parse (zf.getInputStream(zip));
	    for (Study s : studies)
                queue.put(s);
            queue.put(DONE);
	}
    }

    public Map<String, Integer> countTerms (File file) throws Exception {
	ZipFile zf = new ZipFile (file);

        final Map<String, Integer> counts = new HashMap<String, Integer>();
        
	for (Enumeration e = zf.entries(); e.hasMoreElements();) {
	    ZipEntry zip = (ZipEntry)e.nextElement();
	    parse (zf.getInputStream(zip));
	    for (Study s : studies) 
                for (String term : s.interventions) {
                    String[] toks = term.split("[\\s]+");
                    for (String t : toks) {
                        Integer c = counts.get(t);
                        counts.put(t, c!=null ? c+1:1);
                    }
                }
        }

        logger.info("## "+counts.size()+" unique tokens!");
        return counts;
    }

    public void shutdown () {
        threadPool.shutdownNow();
    }

    protected Set<AlignmentRef> align (Study s) {
        Set<AlignmentRef> all = new TreeSet<AlignmentRef>();

        for (String term : s.interventions) {
            AlignmentResults results = alignments.get(term);
            if (results == null) {
                results = align (term);
                if (results != null) {
                    alignments.putIfAbsent(term, results);
                    //logger.info("term \""+term+"\" matched!");
                }
            }

            if (results != null) {
                all.addAll(results.results());
            }
        }

        // nothing found for this study based on the interventions
        // so as the last resort we try the title
        if (all.isEmpty()) {
            AlignmentResults results = align (s.title);
            if (results != null) {
                alignments.putIfAbsent(s.title, results);
                all.addAll(results.results());
            }
        }

        if (!all.isEmpty()) {
            /*
            for (AlignmentRef ar : all) {
                System.err.println("---");
                ar.print(System.err);
            }
            System.err.println(all.size()+" alignment(s)");
            */
            logger.info("+++ "+Thread.currentThread().getName()+" "+s.id+" => "
                        +all.size()+" alignment(s)!");
        }
        else {
            logger.warning("No alignments found for "+s.id+": "+s.title+"\n"
                           +s.toString(s.interventions));
        }

        return all;
    }

    protected AlignmentResults align (String term) {
        SmithWaterman aligner = new SmithWaterman ();
        AlignmentResults results = new AlignmentResults (term);
        // align the given term against the dictionary
        for (Map.Entry<String, Set<String>> me : dictionary.entrySet()) {
            String key = me.getKey();
            for (String s : me.getValue()) {
                //System.out.println("## \""+term+"\" vs \""+s+"\"");

                aligner.align(term, s);
                for (Enumeration<Alignment> en = aligner.alignments();
                     en.hasMoreElements();) {
                    Alignment aln = en.nextElement();
                    
                    // check to see if the extent of this alignment is
                    // a modifier
                    Double mult = modifiers.get(aln.token1().toLowerCase());
                    if (mult == null)
                        mult = 1.;
                    
                    double score = mult* aln.local();
                    // a balance between global & local
                    if (aln.global() > .2 && score > 0.9) {
                        /*
                        System.out.println("++++ \""+term+"\"");
                        System.out.println("---- \""+s+"\"");
                        System.out.println(aln);
                        System.out.println
                            (me.getKey()+" ["
                             +String.format("%1$.3f,", aln.global())
                             +String.format("%1$.3f,", aln.local())
                             +String.format("%1$.3f]", aln.similarity()));
                        */
                        results.add(new AlignmentRef (key, term, s, aln));
                        //System.out.println("## \""+s+"\"");
                        //results.print(System.out);
                    }
                }

                // don't bother with anything else when we have exact match
                if (results.hasExact()) {
                    //logger.info("## Exact match found for \""+term+"\"!");
                    return results;
                }
            }
        }

        return results.size() > 0 ? results : null;
    }

    synchronized void output (Study s, Set<AlignmentRef> matches) {
        if (matches == null || matches.isEmpty()) {
            if (matchStream != null) {
                for (String d : s.interventions) {
                    matchStream.println(s.id+",\""+d+"\",,,,,,");
                }
            }
            return;
        }
        
        Set<String> unique = new HashSet<String>();
        int size = Math.max(s.interventions.size(), maxCandidates);

        for (AlignmentRef ar : matches) {
            if (alignStream != null) {
                alignStream.println("++++ "+String.format("%1$12s",s.id)
                                    +": \""+ar.term+"\"");
                alignStream.println("---- "+String.format("%1$12s",ar.id)
                                    +": \""+ar.ref+"\"");
                alignStream.println(ar.result);
                alignStream.println
                    ("["+String.format("%1$.3f,", ar.result.global())
                     +String.format("%1$.3f,", ar.result.local())
                     +String.format("%1$.3f]", ar.result.similarity()));
            }

            if (matchStream != null) {
                if (unique.add(ar.id) && unique.size() <= size) {
                    matchStream.println
                        (s.id+",\""+ar.term+"\","
                         +ar.id+","
                         +"\""+ar.ref+"\","
                         +String.format("%1$.3f", ar.result.similarity())+","
                         +String.format("%1$.3f", ar.result.global())+","
                         +String.format("%1$.3f", ar.result.local()));
                }
            }
        }
    }

    protected void parse (InputStream is) throws Exception {
        studies.clear();
        SAXParserFactory.newInstance().newSAXParser().parse(is, this);
    }

    public void startDocument () {
    }

    public void endDocument () {
	System.err.println(studies.size() + " studies with drugs!");
	/*
          for (Study s : studies) {
          System.out.print(s.title + ":");
          for (String d : s.interventions) {
          System.out.print(" \"" + d + "\"");
          }
          System.out.println();
          }
	*/
    }
    
    public void startElement (String uri, String localName, String qName, 
			      Attributes attrs) {
	//System.out.println("start " + qName);
        if (qName.equals("search_results")) {
        }
        else if (qName.equals("study")) {
            study = new Study ();
        }
        else if (qName.equals("intervention")) {
            String type = attrs.getValue("type");
            isDrug = type.equals("Drug");
        }
        content.setLength(0);
    }

    public void endElement (String uri, String localName, String qName) {
        String value = content.toString();
	if (qName.equals("study") && !study.interventions.isEmpty()) {
	    studies.add(study);
	}
        else if (qName.equals("nct_id")) {
            study.id = value;
        }
	else if (qName.equals("title")) {
	    study.title = value;
	}
	else if (qName.equals("condition")) {
	    study.conditions.add(value);
	}
	else if (qName.equals("intervention")) {
	    if (//isDrug && 
                //!value.equalsIgnoreCase("placebo") && 
                study.interventions.indexOf(value) < 0) {
		study.interventions.add(value);
	    }
	}
	else if (qName.equals("sponsor")) {
	    study.sponsors.add(value);
	}
	else if (qName.equals("phase")) {
	    study.phases.add(value);
	}
	else if (qName.equals("first_received")) {
	    study.recieved = value;
	}
	else if (qName.equals("start_date")) {
	    study.start = value;
	}
	else if (qName.equals("completion_date")) {
	    study.completion = value;
	}
	else if (qName.equals("last_updated")) {
	    study.updated = value;
	}
	else if (qName.equals("last_verified")) {
	    study.verified = value;
	}
	else if (qName.equals("url")) {
	    study.url = value;
	}
	//System.out.println("end " + qName);
    }
    
    public void characters (char[] ch, int start, int length) {
	content.append(ch, start, length);
    }

    public static void main (String[] argv) throws Exception {
	ClinicalCrawler crawler = new ClinicalCrawler (2);

        if (argv.length > 0) {
            logger.info("LoadDicting dictionary "+argv[0]+"...");
            crawler.loadDict(new FileInputStream (argv[0]));
        }
        
        crawler.loadModifiers();
        
        File file = null;
        if (argv.length > 1) {
            file = new File (argv[1]);
        }
        else {
            file = crawler.download();
        }

        PrintStream match = new PrintStream 
            (new FileOutputStream ("crawler_match.csv"));
        crawler.setMatchStream(match);
        PrintStream align = new PrintStream
            (new FileOutputStream ("crawler_align.txt"));
        crawler.setAlignStream(align);
        
        logger.info("Parsing "+file+"...");
        crawler.parseCT(file);
        crawler.shutdown();

        match.close();
        align.close();
    }
}
