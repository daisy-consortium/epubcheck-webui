package controllers;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.daisy.validation.epubcheck.EpubCheckInvoker;
import org.daisy.validation.epubcheck.Issue;
import org.daisy.validation.epubcheck.Issue.Type;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import play.Logger;
import play.data.FileUpload;
import play.mvc.Controller;

public class Application extends Controller {
	private static final String RESULTS = "results";
	private static final String EPUB_VERSION = "epubVersion";
	private static final String FILENAME = "filename";
	private static final String FILEERROR = "fileError";
	private static final String EPUBCHECK_VERSION = "epubcheckVersion";

	private static final Function<Issue, Map<String, String>> issueToMap = new Function<Issue, Map<String, String>>() {
		public Map<String, String> apply(Issue issue) {
			Map<String, String> map = Maps.newHashMapWithExpectedSize(5);

			String lineNum = issue.lineNo == -1 ? "-" : Integer.toString(issue.lineNo);
			String position = issue.colNo == -1 ? "-" : Integer.toString(issue.colNo);

			map.put("type", issue.type.toString());
			map.put("file", issue.file);
			map.put("lineNr", lineNum);
			map.put("position", position);
			map.put("message", issue.txt);
			return Collections.unmodifiableMap(map);
		}
	};

	public static void index() {
		logVisitor();
		render();
	}

	public static void validate(final FileUpload inputFile) {

		List<Map<String, String>> results = null;
		String filename = null;
		boolean fileError = inputFile == null;
		if (!fileError) {
			results = runEpubcheck(inputFile.asFile().getPath());
			filename = inputFile.getFileName();
		}
		int numIssues = 0;
		if (!fileError) {
			numIssues = results.size();
		}
		logValidateAction(filename, fileError, numIssues);

		renderArgs.put(FILEERROR, fileError);
		renderArgs.put(RESULTS, results);
		renderArgs.put(FILENAME, filename);
		render();
	}

	private static List<Map<String, String>> runEpubcheck(final String file) {
		List<Issue> results = EpubCheckInvoker.run(file);
		// FIXME reports false positive if jar not found
		Iterator<Issue> it = results.iterator();
		while (it.hasNext()) {
			Issue result = (Issue) it.next();
			switch (result.type) {
			case EPUBCHECK_VERSION:
				renderArgs.put(EPUBCHECK_VERSION, result.txt);
				break;
			case EPUB_VERSION:
				renderArgs.put(EPUB_VERSION, result.txt);
				break;
			}
		}
		if (renderArgs.get(EPUBCHECK_VERSION) == null) {
			renderArgs.put(EPUBCHECK_VERSION, EpubCheckInvoker.version());
		}

		return Collections.unmodifiableList(
				Lists.newArrayList(Collections2.transform(Collections2.filter(results, new Predicate<Issue>() {
					public boolean apply(Issue issue) {
						return issue.type != Type.EPUB_VERSION && issue.type != Type.EPUBCHECK_VERSION
								&& issue.type != Type.INFO;
					}
				}), issueToMap)));
	}

	private static void logVisitor() {
		if (request.headers.get("user-agent") != null) {
			Logger.info("Visitor user-agent %s", request.headers.get("user-agent").toString());
		}
		if (request.remoteAddress != null) {
			Logger.info("Visitor IP %s", request.remoteAddress.toString());
		}
	}

	private static void logValidateAction(String filename, boolean fileError, int numIssues) {
		if (request.remoteAddress != null) {
			Logger.info("Validation from IP %s", request.remoteAddress.toString());
		}
		if (fileError) {
			Logger.info("File error (possible cause: too large)", filename);
		} else {
			Logger.info("%d issues found in file '%s'.", numIssues, filename);
		}
	}
}