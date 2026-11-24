import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.sonar.java.ast.visitors.ComplexityVisitor;
import org.sonar.java.model.JParser;
import org.sonar.java.model.JParserConfig;
import org.sonar.java.model.JavaVersionImpl;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.MethodTree;

/** Emits source methods and constructors using the SonarJava complexity visitor. */
class Complexity extends BaseTreeVisitor {
    private final String path;
    private int largest;

    Complexity(String path) {
        this.path = path;
    }

    @Override public void visitMethod(MethodTree tree) {
        if (tree.block() != null) {
            int value = new ComplexityVisitor().getNodes(tree).size();
            System.out.printf("%s:%d\t%s\t%d%n", path, tree.firstToken().range().start().line(), tree.simpleName().name(), value);
            largest = Math.max(largest, value);
        }
        super.visitMethod(tree);
    }

    public static void main(String[] paths) throws Exception {
        if (paths.length == 0) throw new IllegalArgumentException("Supply Java sources");
        var config = JParserConfig.Mode.FILE_BY_FILE.create(new JavaVersionImpl(17), List.of());
        int maximum = 0;
        for (String path : paths) {
            var tree = JParser.parse(config.astParser(), "17", path, Files.readString(Path.of(path)));
            var checker = new Complexity(path);
            tree.accept(checker);
            maximum = Math.max(maximum, checker.largest);
        }
        if (maximum > 9) throw new IllegalStateException("Maximum complexity " + maximum);
    }
}
