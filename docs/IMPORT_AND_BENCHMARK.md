# Import & Benchmark (local developer notes)

This document explains the current import defaults and how to run the local parser/benchmark tool used during development.

## Default batch size
- The project defines a centralized default: `ImportDefaults.DEFAULT_BATCH_SIZE = 100`.
- This default is used by the streaming importer and callers. To change it locally, edit `app/src/main/java/com/example/powerai/data/importer/ImportDefaults.kt`.

## Running unit tests (local)
1. Build and run unit tests:
```bash
./gradlew clean assembleDebug :app:testDebugUnitTest
```

## Running the ParseDryRunner benchmarks (local)
The runner is a small Java program at `tools/ParseDryRunner.java` that calls the import helpers and appends results to `tools/benchmarks.csv`.

1. Compile the runner (the classpath points to locally built classes):
```bash
javac -cp "app/build/tmp/kotlin-classes/debug;app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes" -d tools/bin tools/ParseDryRunner.java
```

2. Run the runner across the assets KB directory with a batch-size sweep (10,50,100,500):
```bash
java -cp "tools/bin;app/build/tmp/kotlin-classes/debug;app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes" ParseDryRunner "app/src/main/assets/kb" "10,50,100,500"
```

3. Results are appended to `tools/benchmarks.csv` (headers include `batchSize`).

## Notes & recommendations
- The runner currently reads each JSON file into memory for repeat runs (fast for local microbenchmarks). For most-accurate peak-memory measurements run the import directly from a stream in the app environment.
- Use `batchSize` trade-offs: smaller reduces peak memory, larger improves throughput. Default `100` is a safe middle ground.
- If you plan CI benchmarks, set the workflow env `RUN_BENCHMARKS=true` (CI workflow created at `.github/workflows/ci.yml`).

## Next local tasks
- Clean Kotlin compiler warnings: many files have unused variables or deprecated API warnings; tidy these for cleaner builds.
