package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.google.auto.service.AutoService;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class TemplateProcessor extends AbstractProcessor {

	// =========================
	// Config
	// =========================
	private static final boolean USE_JSOUP_ENGINE = true;

	/** warnings por @Bind/@State no usados (opcional) */
	private static final boolean STRICT_WARN_UNUSED = false;

	/**
	 * TU @Call real:
	 * jreactive-core/src/main/java/com/ciro/jreactive/annotations/Call.java
	 */
	private static final String CALL_ANNOTATION_FQCN = "com.ciro.jreactive.annotations.Call";
	private static final String CLIENT_ANNOTATION_FQCN = "com.ciro.jreactive.annotations.Client";

	private Trees trees;
	private Filer filer;

	// =========================
	// Patterns (templating)
	// =========================

	// {{state.items.size}} etc.
	private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([\\w#.!-]+(?:\\.[\\w-]+)*)\\s*}}");

	// Remover blocks para que parse HTML no se rompa
	private static final Pattern EACH_BLOCK_PATTERN = Pattern.compile("\\{\\{\\s*#each[\\s\\S]*?\\{\\{\\s*/each\\s*}}",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern IF_BLOCK_PATTERN = Pattern.compile("\\{\\{\\s*#if[\\s\\S]*?\\{\\{\\s*/if\\s*}}",
			Pattern.CASE_INSENSITIVE);

	// :prop="expr"
	private static final Pattern PROP_BIND_PATTERN = Pattern.compile(":(\\w[\\w-]*)\\s*=\\s*\"([^\"]+)\"");

	// Handlers: metodo(...) o metodo
	private static final Pattern HANDLER_PATTERN = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*(?:\\((.*)\\))?\\s*$");

	// Detecta eventos html: @click="x(...)" (simple)
	private static final Pattern EVENT_ATTR = Pattern.compile("(@\\w+)\\s*=\\s*\"([^\"]+)\"");

	// Detecta data-call="x(...)" (fallback)
	private static final Pattern DATA_CALL = Pattern.compile("\\bdata-call\\s*=\\s*\"([^\"]+)\"");

	// Detecta tu estilo de componentes: :onClick="x(...)" / :onSubmit="x(...)"
	private static final Pattern PROP_ONCALL = Pattern.compile(":on(?:Click|Submit|Change|Input)\\s*=\\s*\"([^\"]+)\"");

	// Fix void tags para xmlParser
	private static final Pattern HTML5_VOID_FIX = Pattern.compile(
			"<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)(?<!/)>",
			Pattern.CASE_INSENSITIVE);

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		this.trees = Trees.instance(env);
		this.filer = env.getFiler();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
		for (Element root : round.getRootElements()) {
			if (root.getKind() != ElementKind.CLASS)
				continue;
			TypeElement clazz = (TypeElement) root;

			clazz.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.METHOD)
					.map(e -> (ExecutableElement) e).filter(m -> m.getSimpleName().contentEquals("template"))
					.filter(m -> m.getParameters().isEmpty()).filter(m -> !m.getModifiers().contains(Modifier.ABSTRACT))
					.forEach(tpl -> checkTemplate(clazz, tpl));
		}
		return false;
	}

	private void checkTemplate(TypeElement cls, ExecutableElement tpl) {
		TreePath path = trees.getPath(tpl);
		if (path == null)
			return;

		MethodTree mt = (MethodTree) path.getLeaf();
		BlockTree body = mt.getBody();
		if (body == null)
			return;
		
		

		// 1) Extraer template: SOLO literal string
		String rawHtml = extractTemplateLiteralOrError(cls, tpl, body);
		if (rawHtml == null)
			return;
		
		if (hasAnnotationByName(cls, CLIENT_ANNOTATION_FQCN)) {
	        processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE, 
	            "🚀 [JReactive] Optimizando para modo @Client (CSR): " + cls.getSimpleName());
	        
	        generateClientJs(cls, rawHtml);
	    }

		// 2) Parsear
		ParseResult res = USE_JSOUP_ENGINE ? parseWithJsoup(rawHtml, tpl) : parseWithRegex(rawHtml, tpl);

		// 3) Validar variables
		validateVars(cls, tpl, rawHtml, res.vars, res.refs);

		// 4) Validar eventos -> @Call existe
		validateCalls(cls, tpl, rawHtml, res.calls);

		// 5) Strict (opcional)
		if (STRICT_WARN_UNUSED) {
			warnUnused(cls, tpl, res);
		}
	}

	private String extractTemplateLiteralOrError(TypeElement cls, ExecutableElement tpl, BlockTree body) {
		ReturnTree returnTree = null;

		for (StatementTree st : body.getStatements()) {
			if (st instanceof ReturnTree rt) {
				returnTree = rt;
				break;
			}
		}

		if (returnTree == null) {
			// Si no hay return, no validamos nada (el compilador de Java dará error por su
			// cuenta si falta)
			return null;
		}

		ExpressionTree expr = returnTree.getExpression();

		// 🔥 CORRECCIÓN: Si no es un literal (ej: concatenación string + slot()),
		// emitimos WARNING y retornamos null para saltar la validación, PERO NO ERROR.
		if (!(expr instanceof LiteralTree lt) || !(lt.getValue() instanceof String)) {
			processingEnv.getMessager()
					.printMessage(Diagnostic.Kind.WARNING, "[JReactive] Template dinámico detectado en "
							+ cls.getSimpleName() + ". Se omitirá la validación de bindings en tiempo de compilación.",
							tpl);
			return null;
		}

		return (String) lt.getValue();
	}

	// =========================
	// 🔥 NUEVO: Soporte para Herencia
	// =========================
	private List<Element> getAllMembers(TypeElement te) {
		List<Element> elements = new ArrayList<>();
		TypeElement current = te;
		while (current != null && !current.getQualifiedName().toString().equals("java.lang.Object")) {
			elements.addAll(current.getEnclosedElements());
			TypeMirror superclass = current.getSuperclass();
			if (superclass.getKind() == TypeKind.NONE)
				break;
			current = (TypeElement) processingEnv.getTypeUtils().asElement(superclass);
		}
		return elements;
	}

	// =========================
	// Parsing
	// =========================

	private ParseResult parseWithRegex(String html, ExecutableElement tpl) {
		Set<String> vars = new HashSet<>();
		Set<String> refs = new HashSet<>();
		Set<String> calls = new HashSet<>();

		String cleanHtml = sanitizeForParsing(html);

		// {{var}}
		Matcher m = VAR.matcher(cleanHtml);
		while (m.find())
			vars.add(m.group(1));

		// :prop="expr" -> root
		Matcher p = PROP_BIND_PATTERN.matcher(cleanHtml);
		while (p.find()) {
			String expr = p.group(2).trim();
			String root = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
			if (!root.isBlank())
				vars.add(root);
		}

		// ref="alias"
		Pattern refPattern = Pattern.compile("\\bref\\s*=\\s*\"(\\w+)\"");
		Matcher r = refPattern.matcher(cleanHtml);
		while (r.find()) {
			String alias = r.group(1);
			if (!refs.add(alias)) {
				errorWithContext(tpl, "Alias 'ref=\"" + alias + "\"' duplicado en el mismo template", html,
						"ref=\"" + alias + "\"");
			}
		}

		// Eventos: @click="method(...)" y similares
		Matcher em = EVENT_ATTR.matcher(cleanHtml);
		while (em.find()) {
			String handler = em.group(2);
			String call = parseHandlerMethod(handler);
			if (call != null)
				calls.add(call);
		}

		// data-call="method(...)"
		Matcher dm = DATA_CALL.matcher(cleanHtml);
		while (dm.find()) {
			String handler = dm.group(1);
			String call = parseHandlerMethod(handler);
			if (call != null)
				calls.add(call);
		}

		// :onClick="method(...)" estilo JButton/JForm
		Matcher om = PROP_ONCALL.matcher(cleanHtml);
		while (om.find()) {
			String handler = om.group(1);
			String call = parseHandlerMethod(handler);
			if (call != null)
				calls.add(call);
		}

		return new ParseResult(vars, refs, calls);
	}

	private ParseResult parseWithJsoup(String html, ExecutableElement tpl) {
		Set<String> vars = new HashSet<>();
		Set<String> refs = new HashSet<>();
		Set<String> calls = new HashSet<>();

		String clean = sanitizeForParsing(html);
		String xmlFriendlyHtml = HTML5_VOID_FIX.matcher(clean).replaceAll("<$1$2/>");

		Document doc = Jsoup.parse(xmlFriendlyHtml, "", Parser.xmlParser());

		for (org.jsoup.nodes.Element el : doc.getAllElements()) {
			for (Attribute attr : el.attributes()) {
				String key = attr.getKey();
				String val = attr.getValue();

				if (key.startsWith(":")) {
					// :field="form.name", :options="countries", :greet="hello.newFruit",
					// :onClick="register(form)"
					// Extraemos root para validación de var roots
					String expr = val.trim();
					String root = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
					if (!root.isBlank())
						vars.add(root);

					// además, si es :onClick / :onSubmit etc, también cuenta como call
					if (key.startsWith(":on")) {
						String call = parseHandlerMethod(val);
						if (call != null)
							calls.add(call);
					}
				}

				// Eventos nativos: @click, @input, @change, @submit
				if (key.startsWith("@") || "data-call".equals(key)) {
					String call = parseHandlerMethod(val);
					if (call != null)
						calls.add(call);
				}

				extractVarsFromText(val, vars);
			}

			for (TextNode text : el.textNodes()) {
				extractVarsFromText(text.getWholeText(), vars);
			}

			// ref="..."
			if (el.hasAttr("ref")) {
				String alias = el.attr("ref").trim();
				if (!alias.isEmpty()) {
					if (!refs.add(alias)) {
						errorWithContext(tpl, "Alias 'ref=\"" + alias + "\"' duplicado en el mismo template", html,
								"ref=\"" + alias + "\"");
					}
				}
			}
		}

		return new ParseResult(vars, refs, calls);
	}

	private String sanitizeForParsing(String html) {
		String s = EACH_BLOCK_PATTERN.matcher(html).replaceAll("");
		s = IF_BLOCK_PATTERN.matcher(s).replaceAll("");
		return s;
	}

	private String parseHandlerMethod(String handler) {
		if (handler == null)
			return null;
		String h = handler.trim();
		if (h.isEmpty())
			return null;

		Matcher m = HANDLER_PATTERN.matcher(h);
		if (!m.matches())
			return null;

		// group(1) = nombre del método
		return m.group(1);
	}

	// =========================
	// Validation
	// =========================

	private void validateVars(TypeElement cls, ExecutableElement tpl, String html, Set<String> varsToCheck,
			Set<String> htmlRefs) {

// 1. Recopilar llaves de campos con @Bind
		Map<String, String> bindKeys = new HashMap<>();
		getAllMembers(cls).stream().filter(f -> f.getKind() == ElementKind.FIELD).forEach(f -> {
			Bind b = f.getAnnotation(Bind.class);
			if (b != null) {
				String key = (b.value() != null && !b.value().isBlank()) ? b.value().trim()
						: f.getSimpleName().toString();
				bindKeys.put(key, f.getSimpleName().toString());
			}
		});

// 2. Recopilar llaves de campos con @State
		Map<String, String> stateKeys = new HashMap<>();
		getAllMembers(cls).stream().filter(f -> f.getKind() == ElementKind.FIELD).forEach(f -> {
			State s = f.getAnnotation(State.class);
			if (s != null) {
				String key = (s.value() != null && !s.value().isBlank()) ? s.value().trim()
						: f.getSimpleName().toString();
				stateKeys.put(key, f.getSimpleName().toString());
			}
		});

// 3. Recopilar clases hijas y referencias HTML
		Set<String> childRoots = cls.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.CLASS)
				.map(Element::getSimpleName).map(Object::toString).collect(Collectors.toSet());

		Set<String> allRoots = Stream.of(bindKeys.keySet(), stateKeys.keySet(), childRoots, htmlRefs)
				.flatMap(Set::stream).collect(Collectors.toSet());

// 4. Bucle principal de validación de variables
		for (String v : varsToCheck) {
			if (v == null)
				continue;

			String cleanV = v.trim();
			if (cleanV.isEmpty())
				continue;

// --- SECCIÓN DE EXCEPCIONES (Keywords del sistema y alias dinámicos) ---

// Ignora tokens de control (#if, #each, /if, this, etc)
			if (cleanV.equals("this") || cleanV.startsWith("#") || cleanV.startsWith("/"))
				continue;

// 🔥 EXCEPCIÓN PARA JTABLE: Ignora el alias 'row' ya que es inyectado dinámicamente
			if (cleanV.equals("row") || cleanV.startsWith("row."))
				continue;

// Limpieza de operadores de negación
			cleanV = cleanV.replace("!", "").trim();
			if (cleanV.isEmpty())
				continue;

			String[] parts = cleanV.split("\\.");
			String root = parts[0];
			String last = parts[parts.length - 1];

// Tolerancia para propiedades de utilidad comunes
			if ("size".equals(last) || "length".equals(last))
				continue;

// --- COMPROBACIÓN DE EXISTENCIA ---
			boolean ok = bindKeys.containsKey(cleanV) || bindKeys.containsKey(root) || bindKeys.containsKey(last)
					|| stateKeys.containsKey(root) || allRoots.contains(root) || "store".equals(root);

			if (!ok) {
				errorWithContext(tpl,
						"Variable '{{" + cleanV + "}}' no declarada en " + cls.getSimpleName() + " ni heredada.", html,
						"{{" + cleanV + "}}");
			}
		}
	}

	private void validateCalls(TypeElement cls, ExecutableElement tpl, String html, Set<String> callsToCheck) {

		if (callsToCheck == null || callsToCheck.isEmpty())
			return;

		// 🔥 CAMBIO: getAllMembers en lugar de getEnclosedElements
		Set<String> callMethods = getAllMembers(cls).stream().filter(e -> e.getKind() == ElementKind.METHOD)
				.filter(e -> hasAnnotationByName(e, CALL_ANNOTATION_FQCN))
				.map(e -> ((ExecutableElement) e).getSimpleName().toString()).collect(Collectors.toSet());

		for (String call : callsToCheck) {
			if (call == null || call.isBlank())
				continue;
			if (!callMethods.contains(call)) {
				errorWithContext(tpl, "Evento referencia método '" + call + "' que no existe o no tiene @Call.", html,
						call);
			}
		}
	}

	private boolean hasAnnotationByName(Element el, String fqcn) {
		for (AnnotationMirror am : el.getAnnotationMirrors()) {
			Element annEl = am.getAnnotationType().asElement();
			if (annEl instanceof TypeElement te) {
				String name = te.getQualifiedName().toString();
				if (fqcn.equals(name))
					return true;
			}
		}
		return false;
	}

	private void warnUnused(TypeElement cls, ExecutableElement tpl, ParseResult res) {
		Set<String> usedRoots = new HashSet<>();
		for (String v : res.vars) {
			if (v == null)
				continue;
			String clean = v.replace("!", "").trim();
			if (clean.isEmpty())
				continue;
			usedRoots.add(clean.split("\\.")[0]);
		}
		usedRoots.addAll(res.refs);

		Set<String> bindKeys = cls.getEnclosedElements().stream()
				.filter(f -> f.getKind() == ElementKind.FIELD && f.getAnnotation(Bind.class) != null).map(f -> {
					Bind b = f.getAnnotation(Bind.class);
					return (b.value() != null && !b.value().isBlank()) ? b.value().trim()
							: f.getSimpleName().toString();
				}).collect(Collectors.toSet());

		Set<String> stateKeys = cls.getEnclosedElements().stream()
				.filter(f -> f.getKind() == ElementKind.FIELD && f.getAnnotation(State.class) != null).map(f -> {
					State s = f.getAnnotation(State.class);
					return (s.value() != null && !s.value().isBlank()) ? s.value().trim()
							: f.getSimpleName().toString();
				}).collect(Collectors.toSet());

		for (String k : bindKeys) {
			if (!usedRoots.contains(k)) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
						"[JReactive][strict] @Bind '" + k + "' no se usa en template() de " + cls.getSimpleName(), tpl);
			}
		}
		for (String k : stateKeys) {
			if (!usedRoots.contains(k)) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
						"[JReactive][strict] @State '" + k + "' no se usa en template() de " + cls.getSimpleName(),
						tpl);
			}
		}
	}

	private void extractVarsFromText(String text, Set<String> target) {
		if (text == null || text.isEmpty())
			return;
		Matcher m = VAR.matcher(text);
		while (m.find())
			target.add(m.group(1));
	}

	private void errorWithContext(ExecutableElement tpl, String msg, String html, String needle) {
		String snippet = makeSnippet(html, needle, 70);
		String full = "[JReactive] " + msg + (snippet.isEmpty() ? "" : ("\nContext: " + snippet));
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, full, tpl);
	}

	private String makeSnippet(String html, String needle, int radius) {
		if (html == null || html.isEmpty() || needle == null || needle.isEmpty())
			return "";
		int idx = html.indexOf(needle);
		if (idx < 0)
			return "";

		int start = Math.max(0, idx - radius);
		int end = Math.min(html.length(), idx + needle.length() + radius);
		String s = html.substring(start, end);

		s = s.replace("\r", "").replace("\n", "\\n");
		if (start > 0)
			s = "..." + s;
		if (end < html.length())
			s = s + "...";
		return s;
	}

	private record ParseResult(Set<String> vars, Set<String> refs, Set<String> calls) {
	}
	


	private void generateClientJs(TypeElement cls, String html) {
	    String className = cls.getSimpleName().toString();
	    String fileName = "static/js/jrx/" + className + ".jrx.js";

	    try {
	        javax.tools.FileObject resource = filer.createResource(
	            javax.tools.StandardLocation.CLASS_OUTPUT, "", fileName);

	        try (java.io.Writer writer = resource.openWriter()) {
	            writer.write("// ✨ Generado automáticamente por JReactive APT\n");
	            writer.write("if(!window.JRX_RENDERERS) window.JRX_RENDERERS = {};\n\n");
	            
	            writer.write("window.JRX_RENDERERS['" + className + "'] = function(el, state) {\n");
	            // Guardamos el HTML en una constante
	            String escapedHtml = html.replace("`", "\\`").replace("${", "\\${");
	            writer.write("  const html = `" + escapedHtml + "`;\n");
	            
	            // 🔥 LA CLAVE: Usar el helper central del runtime que ya sabe manejar el número 0
	            writer.write("  el.innerHTML = window.JRX.renderTemplate(html, state);\n");
	            
	            writer.write("  console.log('🎨 Renderizando " + className + " en el cliente:', state);\n");
	            writer.write("};\n");
	        }
	    } catch (java.io.IOException e) {
	        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
	            "❌ Error generando JS para " + className + ": " + e.getMessage());
	    }
	}
}