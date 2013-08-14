package tripod.clinical;

import java.io.Serializable;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * Ternary search tree for string lookup based on an implementation from
 * of Bentley and Sedgewick in Dr. Dobbs.
 */
public class TernarySearchTree<T> implements Serializable {
    private static final long serialVersionUID = 0x0444541a61643883l;
    private static final Logger logger = 
        Logger.getLogger(TernarySearchTree.class.getName());

    class Node {
        char ch;
        Node left, right;
        Object child;
        List<T> values = new ArrayList<T>();

        Node (char ch) { this.ch = ch; }
    }

    private Node root;
    private int size;

    public TernarySearchTree () {
    }

    public int size () { return size; }

    public void insert (String s) {
        insert (s, null);
    }

    public void insert (String s, T value) {
        if (s == null)
            throw new IllegalArgumentException ("Can't insert a null string");
        root = insert (root, s, 0, value);
    }

    protected Node insert (Node p, String s, int i, T v) {
        char ch = i < s.length() ? s.charAt(i) : 0;
        if (p == null) {
            p = new Node (ch);
        }
        if (ch < p.ch) 
            p.left = insert (p.left, s, i, v);
        else if (ch == p.ch) {
            if (ch != 0)
                p.child = insert ((Node)p.child, s, i+1, v);
            // overload the child node to store the string inserted
            else {
                if (p.child == null)
                    ++size; // don't count dup
                p.child = s;
            }
            if (v != null)
                p.values.add(v);
        }
        else // ch > p.ch
            p.right = insert (p.right, s, i, v);

        return p;
    }

    protected void traverse (PrintStream ps, Node p) {
        if (p == null) return;
        traverse (ps, p.left);
        if (p.ch != 0)
            traverse (ps, (Node)p.child);
        else
            ps.println(">> "+p.child);
        traverse (ps, p.right);
    }

    public void dump (PrintStream ps) {
        traverse (ps, root);
    }

    /**
     * membership testing
     */
    protected Node findNode (String s) {
        Node p = root;
        for (int i = 0; p != null; ) {
            char ch = s.charAt(i);
            if (ch < p.ch) p = p.left;
            else if (ch == p.ch) {
                if (++i == s.length())
                    return p;
                p = (Node)p.child;
            }
            else
                p = p.right;
        }
        return null;
    }

    public boolean contains (String s) {
        return null != findNode (s);
    }

    public List<T> values (String s) {
        Node p = findNode (s);
        return p != null ? p.values : null;
    }

    /**
     * Neighbor search
     */
    public List<String> neighbors (String s) {
        return neighbors (s, 2, 5);
    }

    public List<String> neighbors (String s, int dif, int max) {
        List<String> nb = new ArrayList<String>();
        neighbors (nb, root, s, 0, dif, max);
        return nb;
    }

    protected void neighbors (List<String> nb, Node p, String s, 
                              int i, int dif, int max) {
        if (p == null || dif < 0 || nb.size() >= max) 
            return;

        char ch = i < s.length() ? s.charAt(i) : 0;
        if (dif > 0 || ch < p.ch)
            neighbors (nb, p.left, s, i, dif, max);
        if (p.ch == 0) {
            if ((s.length() - i) <= dif)
                nb.add((String)p.child);
        }
        else {
            neighbors (nb, (Node)p.child, s, ch == 0 ? i : (i+1),
                       ch == p.ch ? dif : (dif - 1), max);
        }
        if (dif > 0 || ch > p.ch)
            neighbors (nb, p.right, s, i, dif, max);
    }

    public List<String> prefix (String s, int max) {
        Node p = root;
        for (int i = 0; p != null; ) {
            char ch = s.charAt(i);
            if (ch < p.ch) p = p.left;
            else if (ch == p.ch) {
                if (++i == s.length()) {
                    break;
                }
                p = (Node)p.child;
            }
            else
                p = p.right;
        }

        List<String> prefixes = new ArrayList<String>();
        if (p != null) {
            leafs (prefixes, p.child, max);
        }

        return prefixes;
    }

    protected void leafs (List<String> leafs, Object p, int max) {
        if (p == null || leafs.size() >= max) return;
        if (Node.class.isAssignableFrom(p.getClass())) {
            Node pp = (Node)p;
            leafs (leafs, pp.left, max);
            leafs (leafs, pp.child, max);
            leafs (leafs, pp.right, max);
        }
        else {
            leafs.add((String)p);
        }
    }

    /**
     * partial matching with * is used as "don't care"
     */
    public List<String> partial (String s) {
        List<String> matches = new ArrayList<String>();
        partial (matches, root, s, 0);
        return matches;
    }

    protected void partial (List<String> matches, Node p, String s, int i) {
        if (p == null) return;
        char ch = i < s.length() ? s.charAt(i) : 0;
        if (ch == '*' || ch < p.ch) // * don't care
            partial (matches, p.left, s, i);
        if (ch == '*' || ch == p.ch)
            if (p.ch != 0 && ch != 0)
                partial (matches, (Node)p.child, s, i+1);
        if (ch == 0 && p.ch == 0)
            matches.add((String)p.child);
        if (ch == '*' || ch > p.ch)
            partial (matches, p.right, s, i);
    }

    /************************************
     ** TEST
     ***********************************/

    static void test (int N) throws Exception {
         char[] alpha = new char[] {
            'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o',
            'p','q','r','s','t','u','v','w','x','y','z'
        };
        Random rand = new Random ();
        StringBuilder sb = new StringBuilder ();
        TernarySearchTree tst = new TernarySearchTree ();
        for (int i = 0; i < N; ++i) {
            int l = 1 + rand.nextInt(30);
            for (int j = 0; j < l; ++j) {
                sb.append(alpha[rand.nextInt(alpha.length)]);
                if (rand.nextDouble() < 0.01)
                    sb.append(' ');
            }
            tst.insert(sb.toString());

            if ((i%5000) == 0) {
                logger.info("## "+i+" "
                            +sb+ " mem="
                            +(Runtime.getRuntime().totalMemory()
                              /(1024.*1024.))+"mb");
            }
            sb.delete(0, sb.length());
        }

        for (int i = 0; i < 100; ++i) {
            int l =  2 + rand.nextInt(5);
            for (int j = 0; j < l; ++j) {
                sb.append(alpha[rand.nextInt(alpha.length)]);
            }
            long start = System.currentTimeMillis();
            List<String> nb = tst.neighbors(sb.toString());
            long time = System.currentTimeMillis() - start;
            System.out.print("## Neighbor="+sb+" "+nb.size());
            for (String s : nb) {
                System.out.print(" \""+s+"\"");
            }
            System.out.println(" "+time+"ms");
            sb.delete(0, sb.length());
        }

        for (int i = 0; i < 100; ++i) {
            int l =  2 + rand.nextInt(15);
            for (int j = 0; j < l; ++j) {
                sb.append(alpha[rand.nextInt(alpha.length)]);
                if (rand.nextDouble() < 0.1)
                    sb.append('*');
            }
            long start = System.currentTimeMillis();
            List<String> nb = tst.partial(sb.toString());
            long time = System.currentTimeMillis() - start;
            System.out.print("## Partial="+sb+" "+nb.size());
            for (String s : nb) {
                System.out.print(" \""+s+"\"");
            }
            System.out.println(" "+time+"ms");
            sb.delete(0, sb.length());
        }
    }

    public static void main (String[] argv) throws Exception {
        //test (1500000);

        TernarySearchTree<Integer> tst = new TernarySearchTree<Integer> ();
        String[] strs = new String[]{
            "a", "a", "abd", "cadfa", "adfj", "qerj", "baj",
            "qiery", "ab", "fb", "acv"
        };
        for (int i = 0; i < strs.length; ++i) {
            tst.insert(strs[i], i);
        }
        tst.dump(System.out);

        for (String s : strs) {
            System.out.println("contains(\""+s+"\") = "+tst.contains(s));
            List<Integer> v = tst.values(s);
            if (v != null) {
                System.out.print(" ->");
                for (Integer i : v) {
                    System.out.print(" "+i);
                }
                System.out.println();
            }
        }
        System.out.println("contains(\"bogus\") = "+tst.contains("bogus"));

        System.out.println("-- neighbors");
        for (String a : argv) {
            List<String> nb = tst.neighbors(a, 2, 5);
            System.out.print(a+":");
            for (String n : nb) {
                System.out.print(" "+n);
            }
            System.out.println();
        }

        System.out.println("-- prefix");
        for (String a : argv) {
            List<String> nb = tst.prefix(a, 5);
            System.out.print(a+":");
            for (String n : nb) {
                System.out.print(" "+n);
            }
            System.out.println();
        }

        System.out.println("-- partial");
        String q = "a**";
        List<String> partial = tst.partial(q);
        System.out.print(q+":");
        for (String n : partial) {
            System.out.print(" "+n);
        }
        System.out.println();
    }
}
