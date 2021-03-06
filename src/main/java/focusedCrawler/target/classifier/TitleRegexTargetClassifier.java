package focusedCrawler.target.classifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import focusedCrawler.target.model.Page;

/**
 * Classify whether a page is relevant to a topic by matching a RegExp against the title.
 */
public class TitleRegexTargetClassifier implements TargetClassifier {

    private Pattern pattern;

    public TitleRegexTargetClassifier(String regex) {
        regex = ".*" + regex + ".*";
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public TargetRelevance classify(Page page) throws TargetClassifierException {
        if(regexMatchesTitle(page)) {
            return new TargetRelevance(true, 1.0);
        } else {
            return new TargetRelevance(false, 0.0);
        }
    }
    
    public boolean regexMatchesTitle(Page page) {
        
        String title = page.getParsedData().getTitle();
        if (title != null) {
            Matcher matcher = this.pattern.matcher(title);
            if (matcher.matches()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
        
    }
    
}
