package tripod.clinical;

import java.io.PrintStream;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A simple implementation of the Smith-Waterman local alignment
 * dynamic algorithm.
 */

public class SmithWaterman implements Comparator<SmithWaterman.Alignment> {
    static final Logger logger = Logger.getLogger
        (SmithWaterman.class.getName());

    static final int DEFAULT_MIN_ALIGNMENT = 4;

    public static final int SCORE_MATCH = 2;
    public static final int SCORE_MATCH_SPACE = 0;
    public static final int SCORE_MISMATCH = -1;

    static int DEBUG = 0;
    static {
        try {
            DEBUG = Integer.getInteger("smith-waterman.debug", 0);
        }
        catch (Exception ex) {
        }
    }

    public interface Score {
        int sub (char a, char b);
        int ins (char ch);
        int del (char ch);
    }

    public static class Alignment implements Comparable<Alignment> {
        int[] alignA, alignB;
        int score;
        CharSequence seqi, seqj;
        BitSet extent1, extent2;
        BitSet matches;
        String alignment;
        double globalSim, localSim, similarity;
        List<Pair> trace;

        Alignment (CharSequence seqi, CharSequence seqj, List<Pair> trace) {
            this.trace = trace;
            int size = trace.size();
            alignA = new int[size];
            alignB = new int[size];
            matches = new BitSet (size);
            extent1 = new BitSet (size);
            extent2 = new BitSet (size);

            int k = 0;
            StringBuilder sb1 = new StringBuilder ();
            StringBuilder sb2 = new StringBuilder ();
            StringBuilder sb3 = new StringBuilder ();
            for (Iterator<Pair> it = trace.iterator(); it.hasNext(); ++k) {
                Pair p = it.next();
                alignA[k] = p.i;
                alignB[k] = p.j;

                boolean matched = false;
                if (p.i < 0 && p.j < 0) {
                    break;
                }
                else if (p.i >= 0 && p.j >= 0) {
                    char a = seqi.charAt(p.i), b = seqj.charAt(p.j);
                    sb1.append(a);
                    sb3.append(b);
                    matched = compare (a, b) > 0;
                    sb2.append(matched ? '|' : ' ');
                    if (!matched)
                        score += SCORE_MISMATCH;
                    extent1.set(p.i, true);
                    extent2.set(p.j, true);
                }
                else if (p.i >= 0) {
                    sb1.append(seqi.charAt(p.i));
                    sb3.append('-');
                    sb2.append(' ');
                    extent1.set(p.i, true);
                    score += SCORE_MISMATCH;
                }
                else if (p.j >= 0) {
                    sb1.append('-');
                    sb3.append(seqj.charAt(p.j));
                    sb2.append(' ');
                    extent2.set(p.j, true);
                    score += SCORE_MISMATCH;
                }
                matches.set(k, matched);
            }
            int max = SCORE_MATCH*matches.cardinality();
            score += max;

            // now adjust the score based on the left & right side of
            // the alignment
            Pair p = trace.get(0);
            if (p.i >= 0) {
                /*
                 * this is to treat suffix alignment the same 
                 * as prefix alignment; e.g., 
                 * "curdlan sulfate" vs "uranyl sulfate"
                 * "curdlan sulfate" vs "sulfate uranyl"
                 * both should have local score of 1 for "sulfate"
                 */
                while (p.i < seqi.length() 
                       && Character.isWhitespace(seqi.charAt(p.i)))
                    ++p.i;
                for (k = p.i; --k >= 0 && isPartOfToken (seqi.charAt(k)); )
                    score += SCORE_MISMATCH;
            }

            if (p.j >= 0) {
                while (p.j < seqj.length() 
                       && Character.isWhitespace(seqj.charAt(p.j)))
                    ++p.j;
                for (k = p.j; --k >= 0 && isPartOfToken (seqj.charAt(k)); )
                    score += SCORE_MISMATCH;
            }

            p = trace.get(trace.size()-1);
            if (p.i >= 0) {
                /*
                 * likewise for suffix
                 */
                while (p.i > 0 && Character.isWhitespace(seqi.charAt(p.i)))
                    --p.i;
                for (k = p.i; ++k < seqi.length() 
                         && isPartOfToken (seqi.charAt(k)); )
                    score += SCORE_MISMATCH;
            }

            if (p.j >= 0) {
                while (p.j > 0 && Character.isWhitespace(seqj.charAt(p.j)))
                    --p.j;
                for (k = p.j; ++k < seqj.length()
                         && isPartOfToken (seqj.charAt(k)); )
                    score += SCORE_MISMATCH;
            }

            if (score < 0) score = 0;

            this.seqi = seqi;
            this.seqj = seqj;

            globalSim = (double)score/(seqi.length() + seqj.length());
            localSim = (double)score/max;

            double r = (double)matches.cardinality() 
                / Math.max(seqi.length(), seqj.length());
            similarity = r * localSim + (1 - r)*globalSim;

            alignment = sb1+" ["+token1()+"]\n"+sb2+"\n"
                +sb3 + " ["+token2()+"]";
        }

        static boolean isPartOfToken (char ch) {
            return Character.isLetterOrDigit(ch) || ch == '-';
        }

        public int compareTo (Alignment a) {
            double d = a.similarity - similarity;
            if (d < 0.) return -1;
            if (d > 0.) return 1;

            d = a.localSim - localSim;
            if (d < 0.) return -1;
            if (d > 0.) return 1;

            d = a.globalSim - globalSim;
            if (d < 0.) return -1;
            if (d > 0.) return 1;

            return a.matches.cardinality() - matches.cardinality();
        }

        public BitSet extent1 () { return extent1; }
        public BitSet extent2 () { return extent2; }
        public BitSet overlap1 (BitSet ext) {
            return overlap (extent1, ext);
        }
        public BitSet overlap2 (BitSet ext) {
            return overlap (extent2, ext);
        }

        public static BitSet overlap (BitSet ref, BitSet ext) {
            BitSet and = (BitSet)ref.clone();
            and.and(ext);
            return and;
        }

        // return the extent of this alignment
        public String token1 () {
            return token (alignA, seqi);
        }
        public String token2 () {
            return token (alignB, seqj);
        }

        protected static String token (int[] align, CharSequence seq) {
            int i = align[0];
            for (int k = 1; i < 0; ++k)
                i = align[k];

            int j = align[align.length-1];
            for (int k = align.length-1; j < 0; --k)
                j = align[k];

            // now the extent is the substring that extends both directions
            //  until isPartOfToken return false
            while (i > 0 && isPartOfToken (seq.charAt(i)))
                --i;
            while (i < seq.length() && !isPartOfToken (seq.charAt(i)))
                ++i;
            while (j < seq.length() && isPartOfToken(seq.charAt(j)))
                ++j;

            StringBuilder ext = new StringBuilder ();
            for (int k = i; k < j; ++k)
                ext.append(seq.charAt(k));

            return ext.toString();
        }

        public double similarity () { return similarity; }
        protected List<Pair> trace () { return trace; }
        public int size () { return trace.size(); }
        public int index1 (int k) { return alignA[k]; }
        public int index2 (int k) { return alignB[k]; }
        public int score () { return score; }
        public double global () { return globalSim; }
        public double local () { return localSim; }
        public String toString () { return alignment; }
    }

    public static class Pair {
        public int i, j;
        Pair (int i, int j) {
            this.i = i;
            this.j = j;
        }
        public String toString () {
            return "("+i+","+j+")";
        }
    }

    public static class DefaultScore implements Score {
        public DefaultScore () {}
        public int sub (char a, char b) { return compare (a, b); }
        public int ins (char ch) { return SCORE_MISMATCH; }
        public int del (char ch) { return SCORE_MISMATCH; }
    }

    public static int compare (char a, char b) {
        /*
        if (Character.isWhitespace(a) && Character.isWhitespace(b))
            return SCORE_MATCH_SPACE;
        */
        return Character.toUpperCase(a) == Character.toUpperCase(b) 
            ? SCORE_MATCH : SCORE_MISMATCH;
    }

    protected int minAlignment = DEFAULT_MIN_ALIGNMENT;
    protected Score score = new DefaultScore ();
    protected LinkedList<Alignment> alignments = new LinkedList<Alignment>();

    public SmithWaterman () { }
    public SmithWaterman (Score score) {
        setScore (score);
    }

    public void setScore (Score score) { this.score = score; }
    public Score getScore () { return score; }

    public int align (CharSequence seqi, CharSequence seqj) {
        int max, i, j, n = seqi.length(), m = seqj.length();

        int[][] h = new int[n+1][m+1];
        int[][] path = new int[n+1][m+1]; // paths
        for (i = 1; i <= n; ++i) {
            char ci = seqi.charAt(i-1);
            for (j = 1; j <= m; ++j) {
                char cj = seqj.charAt(j -1);
                int s1 = h[i-1][j-1] + score.sub(ci, cj);
                int s2 = h[i-1][j] + score.del(ci);
                int s3 = h[i][j-1] + score.ins(cj);

                max = Math.max(Math.max(s1, 0), Math.max(s2, s3));
                if (max == s1) path[i][j] |= 1;
                if (max == s2) path[i][j] |= 2;
                if (max == s3) path[i][j] |= 4;

                h[i][j] = max;
            }
        }

        alignments.clear();
        // find best alignment first... 
        max = 0;
        Pair pp = new Pair (n, m);
        for (i = n; i > 0; --i)
            for (j = m; j > 0; --j) 
                if (h[i][j] > max) {
                    max = h[i][j];
                    pp.i = i;
                    pp.j = j;
                }
        LinkedList<Pair> tr = alignment (pp.i, pp.j, path);
        alignments.add(new Alignment (seqi, seqj, tr));
        pp = tr.peekFirst();

        // then the rest of the alignments
        for (i = n; i > 0; --i)
            for (j = m; j > 0; --j)
                // search for the longest alignment...
                if (path[i][j] == 1) {
                    tr = alignment (i, j, path);
                    if (tr.size() > 1) {
                        Pair p = tr.peekLast();
                        Alignment aln = new Alignment (seqi, seqj, tr);

                        if (aln.score() > 0) {
                            Alignment last = alignments.peek();
                            BitSet ext1 = last.overlap1(aln.extent1());
                            BitSet ext2 = last.overlap2(aln.extent2());

                            if (ext1.equals(aln.extent1()) 
                                || ext2.equals(aln.extent2()))
                                aln = null; // containment
                            else if (Math.abs(pp.i-p.i) <= 1 
                                     && Math.abs(pp.j-p.j) <= 1) {
                                /*
                                 * overlap such as this example
                                 * 'testosterone undecanoate' 
                                 * 'TESTOSTERONE DECANOATE'
                                 */

                                List<Pair> merged = new ArrayList<Pair>();
                                merged.addAll(tr);
                                merged.addAll(last.trace());

                                BitSet x = new BitSet ();
                                BitSet y = new BitSet ();
                                for (int k = merged.size(); --k >= 0; ) {
                                    Pair bp = merged.get(k);
                                    if (bp.i >= 0) 
                                        if (x.get(bp.i)) 
                                            bp.i = -bp.i;
                                        else
                                            x.set(bp.i);
                                    if (bp.j >= 0)
                                        if (y.get(bp.j)) 
                                            bp.j = -bp.j;
                                        else
                                            y.set(bp.j);
                                }

                                if (DEBUG > 2) {
                                    System.out.println
                                        ("*** OVERLAP *** "+pp+" "+p);
                                    for (Pair bp : merged) {
                                        System.out.println(bp);
                                    }
                                    System.out.println
                                        (merged.size()+" pairs!");
                                }

                                //alignments.add(aln);
                                aln = new Alignment (seqi, seqj, merged);
                                if (aln.score() == 0)
                                    aln = null;

                                pp = tr.peekFirst();
                            }
                            
                            if (aln != null)
                                alignments.push(aln);
                        }
                    }
                }

        // sort in descreasing score
        Collections.sort(alignments);

        if (DEBUG > 0) {
            System.out.println("Score = ");
            debug (System.err, seqi, seqj, h);
            
            System.out.println("Trace = ");
            debug (System.err, seqi, seqj, path);
        }

        return alignments.size();
    }

    public int compare (Alignment a, Alignment b) {
        int d = b.score() - a.score();
        if (d == 0) {
            d = b.size() - a.size();
        }
        return d;
    }

    LinkedList<Pair> alignment (int i, int j, int[][]path) {
        LinkedList<Pair> tr = new LinkedList<Pair>();

        if (DEBUG > 1)
            System.out.print("("+i+","+j+")");

        Pair pp = null;
        while (i > 0 && j > 0) {
            Pair p = new Pair (i-1, j-1);
            if (pp != null) {
                // negative index denotes gap
                if (p.i == pp.i)
                    p.i = -p.i;
                if (p.j == pp.j)
                    p.j = -p.j;
            }
            tr.push(p);
            path[i][j] = -1; // visited

            if (path[i-1][j-1] == 1) {
                --i;
                --j;
            }
            else if (path[i-1][j] == 1)
                --i;
            else if (path[i][j-1] == 1)
                --j;
            else {
                // bail out early 
                break;
            }

            if (DEBUG > 1)
                System.out.print(" ("+i+","+j+")");
            pp = p;
        }
        if (DEBUG > 1)
            System.out.println();

        return tr;
    }

    public void setMinAlignment (int size) { minAlignment = size;}
    public int getMinAlignment () { return minAlignment; }

    public Alignment getBestAlignment () {
        return alignments.isEmpty() ? null : alignments.iterator().next();
    }

    public Enumeration<Alignment> alignments () { 
        return Collections.enumeration(alignments); 
    }

    static void debug (PrintStream ps, CharSequence seqi, 
                       CharSequence seqj, int[][] a) {
        int n = seqi.length(), m = seqj.length();
        ps.print(" ");
        for (int j = 0; j < m; ++j)
            ps.printf(" %1$2c", seqj.charAt(j));
        ps.println();
        for (int i = 1; i <= n; ++i) {
            ps.printf("%1$c", seqi.charAt(i-1));
            for (int j = 1; j <= m; ++j) {
                ps.printf(" %1$2d", a[i][j]);
            }
            ps.println();
        }
    }

    static int countHoles (BitSet set) {
        int holes = 0;
        for (int i = set.nextClearBit(0); 
             i < set.length(); i = set.nextClearBit(i+1)) {
            ++holes;
        }
        return holes;
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length != 2) {
            System.out.println("Usage: SmithWaterman S1 S2");
            System.exit(1);
        }

        SmithWaterman aligner = new SmithWaterman ();
        String S1 = argv[0], S2 = argv[1];
        //logger.info("## S1="+S1+" S2="+S2);
        int n = aligner.align(argv[0], argv[1]);
        //logger.info("==> "+score);

        System.out.println("### "+n+" ALIGNMENT(S) ###");
        for (Enumeration<Alignment> en = aligner.alignments(); 
             en.hasMoreElements(); ) {
            Alignment aln = en.nextElement();
            System.out.println("---");
            System.out.println(aln);
            System.out.println("## score = "+aln.score());
            System.out.println("## global = "+aln.global());
            System.out.println("## local = "+aln.local());
            System.out.println("## similarity = "+aln.similarity());
        }
    }
}
