import com.example.powerai.data.importer.StreamingJsonResourceImporter;
import java.io.File;
import java.io.FileInputStream;

public class ParseDryRunner {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: ParseDryRunner <file-or-dir>");
            System.exit(2);
        }
        File path = new File(args[0]);
        // optional second arg: comma-separated batch sizes (e.g., "10,50,100")
        int[] batchSizes = new int[] { 100 };
        if (args.length >= 2) {
            try {
                String[] parts = args[1].split(",");
                batchSizes = new int[parts.length];
                for (int i = 0; i < parts.length; i++) batchSizes[i] = Integer.parseInt(parts[i].trim());
            } catch (Throwable t) {
                System.err.println("Invalid batchSizes argument, using default 100");
            }
        }
        if (!path.exists()) {
            System.err.println("Not found: " + path.getAbsolutePath());
            System.exit(2);
        }

        if (path.isDirectory()) {
            java.util.List<File> all = new java.util.ArrayList<>();
            java.util.Deque<File> dq = new java.util.ArrayDeque<>();
            dq.add(path);
            while (!dq.isEmpty()) {
                File cur = dq.removeFirst();
                File[] children = cur.listFiles();
                if (children == null) continue;
                for (File c : children) {
                    if (c.isDirectory()) dq.addLast(c);
                    else if (c.isFile() && c.getName().toLowerCase().endsWith(".json")) all.add(c);
                }
            }
            if (all.isEmpty()) {
                System.err.println("No JSON files under: " + path.getAbsolutePath());
                return;
            }
            for (File f : all) runOne(f, batchSizes);
        } else {
            runOne(path, batchSizes);
        }
    }

    private static void runOne(File f, int[] batchSizes) {
        System.out.println("Benchmarking: " + f.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] data = fis.readAllBytes();
            for (int bs : batchSizes) {
                java.io.ByteArrayInputStream in1 = new java.io.ByteArrayInputStream(data);
                java.lang.Object bench = StreamingJsonResourceImporter.parseDry(in1, 1);
                java.io.ByteArrayInputStream in2 = new java.io.ByteArrayInputStream(data);
                Object r = StreamingJsonResourceImporter.importIntoMemoryDaoBlocking(in2, bs, null, f.getName(), null);
                System.out.println(" -> batchSize=" + bs + " parseDry: " + bench.toString());
                System.out.println(" -> batchSize=" + bs + " import result: " + r.toString());

                // append CSV row to tools/benchmarks.csv
                String base = System.getProperty("user.dir");
                File csv = new File(base, "tools/benchmarks.csv");
                boolean exists = csv.exists();
                try (java.io.FileWriter fw = new java.io.FileWriter(csv, true)) {
                    if (!exists) {
                        fw.write("timestamp,path,batchSize,itemsParsed,peakMemoryBytes,durationMs,importedItems,ftsCount\n");
                    }
                    // bench is StreamingJsonResourceImporter.ParseBenchmark
                    long itemsParsed = 0L;
                    long peak = 0L;
                    long dur = 0L;
                    try {
                        java.lang.Class<?> c = bench.getClass();
                        java.lang.reflect.Field fParsed = c.getDeclaredField("itemsParsed");
                        java.lang.reflect.Field fPeak = c.getDeclaredField("maxMemoryBytes");
                        java.lang.reflect.Field fDur = c.getDeclaredField("durationMs");
                        fParsed.setAccessible(true); fPeak.setAccessible(true); fDur.setAccessible(true);
                        itemsParsed = fParsed.getLong(bench);
                        peak = fPeak.getLong(bench);
                        dur = fDur.getLong(bench);
                    } catch (Throwable _t) { }

                    long importedItems = 0L;
                    int fts = 0;
                    try {
                        java.lang.Class<?> rc = r.getClass();
                        java.lang.reflect.Field fImported = rc.getDeclaredField("importedItems");
                        java.lang.reflect.Field fFts = rc.getDeclaredField("ftsCount");
                        fImported.setAccessible(true); fFts.setAccessible(true);
                        importedItems = fImported.getLong(r);
                        fts = fFts.getInt(r);
                    } catch (Throwable _t) { }

                    String row = String.format(java.time.Instant.now().toString() + ",%s,%d,%d,%d,%d,%d,%d\n",
                            f.getAbsolutePath().replaceAll(",", "\\,"), bs, itemsParsed, peak, dur, importedItems, fts);
                    fw.write(row);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
