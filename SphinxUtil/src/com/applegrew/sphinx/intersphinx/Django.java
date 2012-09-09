package com.applegrew.sphinx.intersphinx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Django {

	private static final String PY_ALL_START_PATTERN_STR = "\\s*__all__\\s*=\\s*\\(";
	private static final String PY_VAR_NAME_CHARS = "a-zA-Z_0-9";
	private static final String PY_VAR_NAME_PATTERN_STR = "[" + PY_VAR_NAME_CHARS + "]+";
	private static final String PY_QUALIFIED_NAME_PATTERN_STR = "[" + PY_VAR_NAME_CHARS + ".]+";
	private static final String SRC_URL_PATTERN_STR = "https://raw.github.com/django/django/${ver}/${module_path}.py";
	private static final String SRC_FALLBACK_URL_PATTERN_STR = "https://raw.github.com/django/django/master/${module_path}.py";
	
	private static final Map<String, Node[]> substitutions = new HashMap<String, Node[]>();
	
	private static final Pattern PY_CLASS_NAME_PATTERN = Pattern.compile("\\s*class\\s+("
			+ PY_VAR_NAME_PATTERN_STR + ")\\(([" + PY_VAR_NAME_CHARS + ",]+)\\):");
	private static final Pattern PY_ALL_PATTERN = Pattern.compile(PY_ALL_START_PATTERN_STR
			+ "([a-zA-Z_0-9,\\s'\"]+)\\)", Pattern.MULTILINE);
	private static final Pattern PY_ALL_START_PATTERN = Pattern.compile(PY_ALL_START_PATTERN_STR);
	private static final Pattern INTERSPHINX_PY_LINE = Pattern.compile("\\s*(" + PY_QUALIFIED_NAME_PATTERN_STR
			+ ")\\s+(\\S*:\\S*)\\s+(\\S+)\\s+(\\S+)\\s+(.*)");
	
	static {
		substitutions.put("django.forms", new Node[] {
				new Node("django.forms.widgets"),
				new Node("django.forms.fields")
		});
		substitutions.put("django.db.models", new Node[] {
				new PredefinedClassNode("django.db.models.base.Model"),
				new PredefinedClassNode("django.db.models.manager.Manager"),
				new PredefinedClassNode("django.db.models.query.Q"),
				new Node("django.db.models.fields"),
				new Node("django.db.models.fields.files"),
				new Node("django.db.models.fields.related"),
				new Node("django.db.models.aggregates")
		});
	}
	
	public static Set<String> getAllPublicPythonClassNames(InputStream file) throws IOException {
		Set<String> classes = new TreeSet<String>();
		
		BufferedReader input = new BufferedReader(new InputStreamReader(file));
		boolean isAll = false;
		StringBuilder allKeywordInput = null;
		try {
			String inputLine;
			do {
				inputLine = input.readLine();
				if (inputLine != null) {
					inputLine = inputLine.trim();
					
					if (!isAll) {
						if (PY_ALL_START_PATTERN.matcher(inputLine).find()) {
							allKeywordInput = new StringBuilder();
							allKeywordInput.append(inputLine);
							isAll = true;
							continue;
						}
					} else {
						allKeywordInput.append(inputLine);
						if (inputLine.endsWith(")")) {
							isAll = false;
							Matcher match = PY_ALL_PATTERN.matcher(allKeywordInput);
							if (match.find()) {
								classes.clear();
								
								String[] classNames = match.group(1).split("\\s*,\\s*");
								for (String className : classNames) {
									className = className.replaceAll("\"|'", ""); // Removing quotes around the class names.
									classes.add(className);
								}
								return classes;
							}
						}
						continue;
					}
					
					Matcher match = PY_CLASS_NAME_PATTERN.matcher(inputLine);
					if (match.find()) {
						String className = match.group(1);
						classes.add(className);
					}
				}
			} while(inputLine != null);
		} finally {
			input.close();
		}
		
		return classes;
	}
	
	public static String getHeaderValue(String header, int lineNumber) {
		return header.split(EncodeDecodeObjectInv.NL)[lineNumber - 1].split(":")[1].trim();
	}
	
	public static void fixDjangoObjectsInv(InputStream input, OutputStream output) throws IOException {
		Set<String> allPatterns = substitutions.keySet();
		Map<Node, Set<String>> nodeClassesMapCahce = new HashMap<Node, Set<String>>();
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		String header = EncodeDecodeObjectInv.decodeObjectsInv(input, bout, false);
		
		if (!"Django".equalsIgnoreCase(getHeaderValue(header, 2))) {
			System.err.println("Provide objects.inv is not Django's.");
			System.exit(2);
		}
		
		String djangoVersion = getHeaderValue(header, 3);
		
		BufferedReader body = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bout.toByteArray()), "UTF-8"));
		bout.reset();
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(bout, "UTF-8"));
		String lineRead;
		do {
			lineRead = body.readLine();
			if (lineRead != null) {
				out.write(lineRead + EncodeDecodeObjectInv.NL);
				Matcher match = INTERSPHINX_PY_LINE.matcher(lineRead);
				if (match.matches()) {
					String qName = match.group(1);
					String type = match.group(2);
					String className = null;
					String qModuleName = null;
					String otherObj = null;
					{
						String[] classNameParts = qName.split("\\.");
						if ("py:class".equals(type)) {
							className = classNameParts[classNameParts.length - 1];
							qModuleName = qName.replaceAll("\\." + className + "$", "");
						} else if (type.startsWith("py:") && classNameParts.length >= 2) {
							className = classNameParts[classNameParts.length - 2];
							otherObj = classNameParts[classNameParts.length - 1];
							qModuleName = qName.replaceAll("\\." + className + "\\." + otherObj + "$", "");
						}
						
					}
					String url = match.group(4);
					if (url.endsWith("$")) {
						url = url.substring(0, url.length() - 1) + qName;
					}
					
					if (qModuleName != null && allPatterns.contains(qModuleName)) {
						Node[] substituteNodes = substitutions.get(qModuleName);
						for (Node substitute : substituteNodes) {
							Set<String> classes = nodeClassesMapCahce.get(substitute);
							if (classes == null) {
								classes = substitute.fetchClassNames(djangoVersion);
								nodeClassesMapCahce.put(substitute, classes);
							}
							
							if (classes.contains(className)) {
								String newQName = substitute.getSubstitutedName(className, otherObj);
								
								String newEntry = newQName + " " + type + " 1 " + url + " -";
								out.write(newEntry + EncodeDecodeObjectInv.NL);
								
								break;
							}
						}
					}
				}
			}
		} while (lineRead != null);
		
		out.close();
		EncodeDecodeObjectInv.encodeObjectsInv(new ByteArrayInputStream(bout.toByteArray()), output, header);	
	}
	
	private static class Node {
		protected String module;
		protected String urlPattern;
		
		Node(String module) {
			this.module = module;
			if (module != null) {
				urlPattern = SRC_URL_PATTERN_STR.replace("${module_path}", module.replace('.', '/'));
			}
		}
		
		String getSubstitutedName(String className, String otherObj) {
			if (otherObj == null) {
				return this.module + "." + className;
			} else {
				return this.module + "." + className + "." + otherObj;
			}
		}
		
		protected String getUrl(String version) {
			return urlPattern.replace("${ver}", version);
		}
		
		protected String getAltUrl(String version) {
			return SRC_URL_PATTERN_STR
					.replace("${module_path}", module.replace('.', '/') + "/__init__")
					.replace("${ver}", version);
		}
		
		protected String getFallbackUrl() {
			return SRC_FALLBACK_URL_PATTERN_STR.replace("${module_path}", module.replace('.', '/'));
		}
		
		Set<String> fetchClassNames(String version) throws IOException {
			String url = getUrl(version);
			int retry = 0;
			
			do {
				URL urlConn = new URL(url);
				URLConnection conn = urlConn.openConnection();
				if (conn instanceof HttpURLConnection) {
					HttpURLConnection http = (HttpURLConnection) conn;
					if (http.getResponseCode() == 404) {
						if (retry == 0) {
							url = getAltUrl(version);
							retry = 1;
							continue;
						} if (retry == 1) {
							url = getFallbackUrl();
							retry = 2;
							continue;
						} else {
							retry = 0;
							System.err.println("Fallback url: " + url + ", too failed.");
						}
					}
				}
				return getAllPublicPythonClassNames(conn.getInputStream());
			} while (retry == 0);
			return new TreeSet<String>();
		}
	}
	
	private static class PredefinedClassNode extends Node {
		protected String qualifiedClassName;
		protected String className;
		
		PredefinedClassNode(String qualifiedClassName) {
			super(null);
			this.qualifiedClassName = qualifiedClassName;
			String [] parts = qualifiedClassName.split("\\.");
			this.className = parts[parts.length - 1];
		}
		
		String getSubstitutedName(String className, String otherObj) {
			if (otherObj == null) {
				return qualifiedClassName;
			} else {
				return qualifiedClassName + "." + otherObj;
			}
		}
		Set<String> fetchClassNames(String version) throws IOException {
			Set<String> s = new TreeSet<String>();
			s.add(className);
			return s;
		}
	}
}
