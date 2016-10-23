// List of main classes in the test project
expectedMains = [ "MainExample1", "test.MainExample2", "test.pkg.MainExample3" ];
expectedCount = expectedMains.size();
// Expected output file
File output = new File("target/it/simple-it/target/entry-points.txt");
lines = Arrays.asList(output.text.split("\n"));
assert lines.size() == expectedCount;
for (i = 0; i < lines.size(); i++) {
	assert lines.contains(expectedMains[i]);
}
