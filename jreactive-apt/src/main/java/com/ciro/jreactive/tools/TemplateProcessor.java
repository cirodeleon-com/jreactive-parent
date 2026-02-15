package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
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
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.io.IOException;
import javax.lang.model.element.VariableElement;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")

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
	// Evita regenerar en rondas múltiples
	private final Set<String> _generatedAccessors = new HashSet<>();
	private final Set<String> _generatedClientJs  = new HashSet<>();
	// secuencia para dar IDs únicos a ComponentNode (evita colisiones de vars locales)
	private int __nodeSeq = 0;


	

	// =========================
	// Patterns (templating)
	// =========================

	// 1. Regex combinada: Bloques | Variables | Componentes (<Comp ... />)
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\{\\{\\s*(#if|#each|/if|/each|else)\\s*([^}]*)\\s*}}|" + 
        "\\{\\{\\s*([\\w#.!-]+(?:\\.[\\w-]+)*)\\s*}}|" +           
        "<([A-Z][\\w]*|slot)([^>]*?)(?:/>|>(.*?)</\\4>)",                
        Pattern.DOTALL
    );

    // 2. Mantenemos VAR para validaciones legacy
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
    public SourceVersion getSupportedSourceVersion() {
        // Esto usa siempre la versión máxima disponible en el compilador actual.
        // Funciona en Java 17, 21, 25... y evita el error de "symbol not found".
        return SourceVersion.latest();
    }

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
	    // ✅ En la última ronda no generamos nada
	    if (round.processingOver()) return false;

	    for (Element root : round.getRootElements()) {
	        if (root.getKind() != ElementKind.CLASS) continue;

	        TypeElement clazz = (TypeElement) root;

	        // ✅ Ignora clases generadas por tu propio APT
	        String simple = clazz.getSimpleName().toString();
	        if (simple.endsWith("__Accessor")) continue;

	        clazz.getEnclosedElements().stream()
	            .filter(e -> e.getKind() == ElementKind.METHOD)
	            .map(e -> (ExecutableElement) e)
	            .filter(m -> m.getSimpleName().contentEquals("template"))
	            .filter(m -> m.getParameters().isEmpty())
	            .filter(m -> !m.getModifiers().contains(Modifier.ABSTRACT))
	            .forEach(tpl -> checkTemplate(clazz, tpl));

	        if (shouldGenerateAccessor(clazz)) {
	            generateJavaAccessor(clazz);
	        }
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
			//String root = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
			//if (!root.isBlank())
			//	vars.add(root);
			extractRootsFromExpr(expr, vars);
			
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
					//String root = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
					//if (!root.isBlank())
					//	vars.add(root);
					
					extractRootsFromExpr(expr, vars);

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
	
	private TypeMirror getGenericType(TypeMirror collectionType) {
	    if (collectionType instanceof javax.lang.model.type.DeclaredType declaredType) {
	        List<? extends TypeMirror> args = declaredType.getTypeArguments();
	        if (!args.isEmpty()) return args.get(0);
	    }
	    // Si no tiene generics, devolvemos Object
	    return processingEnv.getElementUtils().getTypeElement("java.lang.Object").asType();
	}

	// =========================
	// Validation
	// =========================

	private void validateVars(TypeElement cls, ExecutableElement tpl, String html, Set<String> varsToCheck, Set<String> htmlRefs) {
	    // 1. Mapa de Tipos Raíz (Campo -> Tipo Java)
	    Map<String, TypeMirror> rootTypes = new HashMap<>();
	    getAllMembers(cls).stream()
	        .filter(f -> f.getKind() == ElementKind.FIELD)
	        .forEach(f -> {
	            if (f.getAnnotation(Bind.class) != null || f.getAnnotation(State.class) != null) {
	                String key = getLogicalName(f);
	                rootTypes.put(key, f.asType());
	            }
	        });

	    // 2. Extraer alias de bucles #each para saber qué tipos son (Ej: pedidos as p)
	    Map<String, TypeMirror> loopAliases = extractLoopAliases(html, rootTypes);

	    // 3. Clases hijas para navegación estática
	    Set<String> childRoots = cls.getEnclosedElements().stream()
	            .filter(e -> e.getKind() == ElementKind.CLASS)
	            .map(e -> e.getSimpleName().toString())
	            .collect(Collectors.toSet());

	    for (String varPath : varsToCheck) {
	        if (varPath == null || varPath.isBlank()) continue;
	        if (isKeyword(varPath)) continue;

	        String cleanPath = varPath.replace("!", "").trim();
	        String[] parts = cleanPath.split("\\.");
	        String root = parts[0];

	        // --- Excepciones Dinámicas (Mantiene funcionalidad actual) ---
	        if (root.equals("store") || htmlRefs.contains(root) || childRoots.contains(root)) continue;

	        // --- Nueva Lógica: ¿Es un alias de bucle o una raíz del componente? ---
	        TypeMirror currentType = loopAliases.containsKey(root) ? loopAliases.get(root) : rootTypes.get(root);

	        if (currentType == null) {
	            // Caso especial JTable 'row'
	            if (root.equals("row")) continue; 
	            errorWithContext(tpl, "Variable raíz '" + root + "' no declarada en " + cls.getSimpleName(), html, "{{" + varPath + "}}");
	            continue;
	        }

	        // --- 🚀 NAVEGACIÓN PROFUNDA DE TIPOS ---
	        for (int i = 1; i < parts.length; i++) {
	            String memberName = parts[i];
	            if (memberName.equals("size") || memberName.equals("length")) break;

	            Element currentElement = processingEnv.getTypeUtils().asElement(currentType);
	            if (currentElement == null || !(currentElement instanceof TypeElement typeElement)) break;

	            Element member = findMemberInType(typeElement, memberName);

	            if (member == null) {
	                errorWithContext(tpl, 
	                    String.format("La propiedad '%s' no existe en el tipo %s", memberName, currentElement.getSimpleName()), 
	                    html, "{{" + varPath + "}}");
	                break;
	            }
	            currentType = member.asType();
	        }
	    }
	}

	/**
	 * Analiza el HTML buscando {{#each lista as alias}} y determina el tipo del alias
	 * basándose en el tipo genérico de la lista.
	 */
	private Map<String, TypeMirror> extractLoopAliases(String html, Map<String, TypeMirror> rootTypes) {
	    Map<String, TypeMirror> aliases = new HashMap<>();
	    Pattern eachPattern = Pattern.compile("\\{\\{\\s*#each\\s+([\\w.]+)\\s+as\\s+(\\w+)\\s*}}");
	    Matcher m = eachPattern.matcher(html);
	    
	    while (m.find()) {
	        String listPath = m.group(1);
	        String alias = m.group(2);
	        
	        // Buscamos el tipo de la lista (asumiendo que es una raíz directa por ahora)
	        TypeMirror listType = rootTypes.get(listPath.split("\\.")[0]);
	        if (listType != null) {
	            aliases.put(alias, getGenericType(listType));
	        }
	    }
	    return aliases;
	}

	private String getLogicalName(Element e) {
	    Bind b = e.getAnnotation(Bind.class);
	    if (b != null && !b.value().isBlank()) return b.value().trim();
	    State s = e.getAnnotation(State.class);
	    if (s != null && !s.value().isBlank()) return s.value().trim();
	    return e.getSimpleName().toString();
	}

	private boolean isKeyword(String v) {
	    return v.equals("this") || v.startsWith("#") || v.startsWith("/") || v.equals("else");
	}

	private Element findMemberInType(TypeElement type, String name) {
	    return processingEnv.getElementUtils().getAllMembers(type).stream()
	        .filter(m -> {
	            String sName = m.getSimpleName().toString();
	            return sName.equals(name) || 
	                   sName.equals("get" + capitalize(name)) || 
	                   sName.equals("is" + capitalize(name));
	        })
	        .findFirst()
	        .orElse(null);
	}

	private String capitalize(String s) {
	    if (s == null || s.isEmpty()) return s;
	    return s.substring(0, 1).toUpperCase() + s.substring(1);
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

        if (!_generatedClientJs.add(fileName)) {
            return; // ✅ ya lo generamos en otra ronda
        }

        try {
            javax.tools.FileObject resource = filer.createResource(
                    javax.tools.StandardLocation.CLASS_OUTPUT, "", fileName);

            try (java.io.Writer writer = resource.openWriter()) {
                writer.write("// ✨ Componente CSR O(1) generado por JReactive\n");
                writer.write("if(!window.JRX_RENDERERS) window.JRX_RENDERERS = {};\n\n");

                writer.write("window.JRX_RENDERERS['" + className + "'] = {\n");
                writer.write("  // Devuelve el template crudo para ser hidratado\n");
                writer.write("  getTemplate: function() {\n");

                // Escapamos backticks y ${} para que sea un template literal válido de JS
                String escapedHtml = html
                        .replace("\\", "\\\\") // Escapar backslashes primero
                        .replace("`", "\\`")   // Escapar comillas invertidas
                        .replace("${", "\\${") // Evitar interpolación JS nativa
                        .replace("\n", "\\n") // Aplanar saltos de línea (opcional, por limpieza)
                        .replace("\r", "");

                writer.write("    return `" + escapedHtml + "`;\n");
                writer.write("  }\n");
                writer.write("};\n");
            }
        } catch (java.io.IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "❌ Error generando JS para " + className + ": " + e.getMessage());
        }
    }
	
	// =================================================================================
    // ⚡ LÓGICA AOT (GENERACIÓN DE ACCESSORS) - FASE 5
    // =================================================================================

	private boolean shouldGenerateAccessor(TypeElement clazz) {
	    // 1. Buscamos si tiene elementos reactivos explícitos
	    for (Element e : clazz.getEnclosedElements()) {
	        if (e.getAnnotation(State.class) != null || 
	            e.getAnnotation(Bind.class) != null || 
	            e.getAnnotation(Call.class) != null) {
	            return true;
	        }
	    }

	    // 🔥 FIX: Si tiene un template definido, TAMBIÉN necesitamos accessor 
	    // para pre-compilar el HTML (renderStatic), aunque no tenga estado.
	    if (findTemplateMethod(clazz) != null) {
	        return true;
	    }

	    return false;
	}

    private void generateJavaAccessor(TypeElement clazz) {
        String pkg = processingEnv.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
        String className = clazz.getSimpleName().toString();
        String accessorName = className + "__Accessor";
        
        String fqcn = pkg + "." + accessorName;
        if (!_generatedAccessors.add(fqcn)) {
            return; // ✅ ya fue generado en una ronda anterior
        }


        try {
            JavaFileObject file = filer.createSourceFile(pkg + "." + accessorName, clazz);
            try (Writer w = file.openWriter()) {
                w.write("package " + pkg + ";\n\n");
                w.write("import com.ciro.jreactive.spi.ComponentAccessor;\n");
                w.write("import com.ciro.jreactive.spi.AccessorRegistry;\n");
                w.write("import javax.annotation.processing.Generated;\n\n");

                w.write("@Generated(\"JReactiveAPT\")\n");
                w.write("public class " + accessorName + " implements ComponentAccessor<" + className + "> {\n\n");

                // Auto-registro estático: Se registra solo al cargar la clase
                w.write("    static {\n");
                w.write("        AccessorRegistry.register(" + className + ".class, new " + accessorName + "());\n");
                w.write("    }\n\n");

                generateWriteMethod(w, clazz, className);
                generateReadMethod(w, clazz, className);
                generateCallMethod(w, clazz, className);
                
                generateResolvePathHelper(w);
                
                ExecutableElement tplMethod = findTemplateMethod(clazz);
                if (tplMethod != null) {
                    TreePath path = trees.getPath(tplMethod);
                    if (path != null && path.getLeaf() instanceof MethodTree mt) {
                        String rawHtml = extractTemplateLiteralOrError(clazz, tplMethod, mt.getBody());
                        if (rawHtml != null) {
                            generateRenderStatic(w, className, rawHtml);
                        }
                    }
                }

                w.write("}\n");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error generando accessor: " + e.getMessage());
        }
    }

    private void generateWriteMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public void write(" + className + " t, String p, Object v) {\n");
        w.write("        switch (p) {\n");

        for (Element e : clazz.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD) {
                String name = e.getSimpleName().toString();
                String key = name;
                
                State s = e.getAnnotation(State.class);
                Bind b = e.getAnnotation(Bind.class);
                if (s != null && !s.value().isBlank()) key = s.value();
                if (b != null && !b.value().isBlank()) key = b.value();

                if (s != null || b != null) {
                	
                	if (e.getModifiers().contains(Modifier.PRIVATE)) {
                        processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, 
                            "🚨 AOT ERROR: El campo '" + e.getSimpleName() + "' no puede ser private. " +
                            "JReactive necesita acceso (package-private o public) para generar el codigo sin reflexion.", 
                            e
                        );
                        continue; // Saltamos para no generar código roto
                    }
                	
                    TypeMirror type = e.asType();
                    w.write("            case \"" + key + "\":\n");
                    w.write("                t." + name + " = " + castLogic(type, "v") + ";\n");
                    w.write("                break;\n");
                }
            }
        }
        w.write("        }\n");
        w.write("    }\n\n");
    }

    private void generateReadMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public Object read(" + className + " t, String p) {\n");
        w.write("        switch (p) {\n");

        for (Element e : getAllMembers(clazz)) {
            if (e.getKind() == ElementKind.FIELD) {
                State s = e.getAnnotation(State.class);
                Bind b = e.getAnnotation(Bind.class);
                if (s != null || b != null) {
                    String fieldName = e.getSimpleName().toString();
                    String key = fieldName;
                    if (s != null && !s.value().isBlank()) key = s.value().trim();
                    if (b != null && !b.value().isBlank()) key = b.value().trim();
                    
                    w.write("            case \"" + key + "\": {\n");
                    w.write("                Object val = t." + fieldName + ";\n");
                    // 🔥 DESENVOLVER: Si es ReactiveVar o Type, devolvemos el .get()
                    w.write("                if (val instanceof com.ciro.jreactive.ReactiveVar<?> rv) return rv.get();\n");
                    w.write("                if (val instanceof com.ciro.jreactive.Type<?> tp) return tp.get();\n");
                    w.write("                return val;\n");
                    w.write("            }\n");
                }
            }
        }
        w.write("            default: return null;\n");
        w.write("        }\n");
        w.write("    }\n\n");
    }

    private void generateCallMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public Object call(" + className + " t, String m, Object... args) {\n");
        w.write("        switch (m) {\n");

        for (Element e : clazz.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && e.getAnnotation(Call.class) != null) {
                ExecutableElement method = (ExecutableElement) e;
                String methodName = method.getSimpleName().toString();
                
                w.write("            case \"" + methodName + "\":\n");
                w.write("                t." + methodName + "(");
                List<? extends VariableElement> params = method.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    TypeMirror pt = params.get(i).asType();
                    w.write(castLogic(pt, "args[" + i + "]"));
                    if (i < params.size() - 1) w.write(", ");
                }
                w.write(");\n");
                
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    w.write("                return null;\n");
                } else {
                    w.write("                return null;\n"); 
                }
            }
        }
        w.write("            default: throw new IllegalArgumentException(\"Method not found: \" + m);\n");
        w.write("        }\n");
        w.write("    }\n\n");
    }

    
    
    private String castLogic(TypeMirror type, String varName) {
        String t;
        // 🔥 FIX: Obtener el nombre completo (FQN) para clases internas/externas
        if (type instanceof javax.lang.model.type.DeclaredType dt) {
            t = ((TypeElement) dt.asElement()).getQualifiedName().toString();
        } else {
            t = type.toString();
        }

        // Limpieza de anotaciones si el compilador las incluye (ej: @NotNull java.lang.String)
        if (t.contains(" ")) {
            t = t.substring(t.lastIndexOf(' ') + 1);
        }

        // Tipos primitivos y wrappers
        if (t.equals("int") || t.equals("java.lang.Integer")) return "((Number) " + varName + ").intValue()";
        if (t.equals("long") || t.equals("java.lang.Long")) return "((Number) " + varName + ").longValue()";
        if (t.equals("double") || t.equals("java.lang.Double")) return "((Number) " + varName + ").doubleValue()";
        if (t.equals("boolean") || t.equals("java.lang.Boolean")) return "(Boolean) " + varName;
        if (t.equals("java.lang.String")) return "(String) " + varName;
        
        // Fallback (Casteo seguro con FQN)
        return "(" + t + ") " + varName;
    }
    
    
    private void generateRenderMethod(Writer w, String html, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public String render(" + className + " t) {\n");
        w.write("        StringBuilder sb = new StringBuilder(" + (html.length() * 2) + ");\n");

        // 1. Parsear el template buscando {{var}}
        Matcher m = VAR.matcher(html);
        int lastIdx = 0;
        
        while (m.find()) {
            // Parte estática antes de la variable
            String staticPart = html.substring(lastIdx, m.start());
            if (!staticPart.isEmpty()) {
                w.write("        sb.append(\"" + escapeJava(staticPart) + "\");\n");
            }

            // Parte dinámica (La variable)
            String varPath = m.group(1);
            w.write("        sb.append(readDeep(t, \"" + varPath + "\"));\n");
            
            lastIdx = m.end();
        }

        // Última parte estática
        if (lastIdx < html.length()) {
            w.write("        sb.append(\"" + escapeJava(html.substring(lastIdx)) + "\");\n");
        }

        w.write("        return sb.toString();\n");
        w.write("    }\n\n");
    }

    
    
    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
	
    
    private void generateReadDeep(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    private String readDeep(" + className + " t, String path) {\n");
        w.write("        try {\n");
        w.write("            Object val = null;\n");
        w.write("            switch(path) {\n");

        // Generar casos directos para cada @State/@Bind
        getAllMembers(clazz).stream()
            .filter(f -> f.getAnnotation(State.class) != null || f.getAnnotation(Bind.class) != null)
            .forEach(f -> {
                String name = f.getSimpleName().toString();
                try {
                    w.write("                case \"" + name + "\": val = t." + name + "; break;\n");
                } catch (IOException e) {}
            });

        w.write("                default: return \"\";\n");
        w.write("            }\n");
        w.write("            return val == null ? \"\" : String.valueOf(val);\n");
        w.write("        } catch (Exception e) { return \"\"; }\n");
        w.write("    }\n");
    }
    

    private void generateRenderStatic(Writer w, String className, String rawHtml) throws IOException {
        w.write("    @Override\n");
        w.write("    public String renderStatic(" + className + " t) {\n");
        
        // 1. Escudo en Tiempo de Ejecución (para cuando la app corre)
        w.write("        try {\n");

        // 2. Escudo en Tiempo de Compilación (para que el APT no se muera)
        try {
            w.write("            int __jrxRefSeq = 0;\n");
            w.write("            StringBuilder sb = new StringBuilder(" + (int)(rawHtml.length() * 2) + ");\n");
            w.write("            sb.append(\"<div id=\\\"\").append(t.getId()).append(\"\\\">\");\n");
            w.write("            sb.append(t._getBundledResources());\n");

            // Intento de procesar HTML. Si Jsoup falla aquí, lo atrapamos abajo.
            String processedHtml = qualifyEventsAtCompileTime(rawHtml);
            
            this.__nodeSeq = 0;
            
            List<Node> nodes = parseToNodes(processedHtml);
            Set<String> componentScope = new HashSet<>();

            for (Node node : nodes) {
                node.generate(w, componentScope, "sb");
            }

            w.write("            sb.append(\"</div>\");\n");
            w.write("            return sb.toString();\n");

        } catch (Throwable e) {
            // 🔥 SI FALLA EL COMPILADOR:
            // 1. Mandamos el error a la consola de Maven (rojo)
        	processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
        			  "⚠️ AOT deshabilitado para " + className + " por error: " + e);

        	
            
            
            // 2. Imprimimos el stacktrace completo para que lo veas
            e.printStackTrace();

            // 3. Escribimos código Java válido para que el archivo no quede roto
            w.write("            return \"\";\n");
        }

        // Cierre del escudo de ejecución
        w.write("        } catch (Exception e) { return \"Error Runtime: \" + e.getMessage(); }\n");
        w.write("    }\n");
    }


    
    
    private String qualifyEventsAtCompileTime(String html) {
        if (!USE_JSOUP_ENGINE) return html; 
        try {
            String xmlFriendly = HTML5_VOID_FIX.matcher(html).replaceAll("<$1$2/>");
            Document doc = Jsoup.parse(xmlFriendly, "", Parser.xmlParser());
            // 🔥 Configuración para que no formatee y rompa layouts
            doc.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html);
            
            Set<String> loopAliases = new HashSet<>();
            Matcher mEach = Pattern.compile("#each\\s+[\\w.]+\\s+as\\s+(\\w+)").matcher(html);
            while(mEach.find()) loopAliases.add(mEach.group(1));

            for (org.jsoup.nodes.Element el : doc.getAllElements()) {
                List<Attribute> toAdd = new ArrayList<>();
                List<String> toRemove = new ArrayList<>();
                
                for (Attribute attr : el.attributes()) {
                    String key = attr.getKey();
                    if (key.startsWith("@") || key.equals("data-call")) {
                        String val = attr.getValue().trim();
                        if (val.contains("{{") || val.contains("#")) continue;
                        
                        Matcher m = Pattern.compile("^(\\w+)(?:\\((.*)\\))?$").matcher(val);
                        if (m.matches()) {
                            String method = m.group(1);
                            String args = m.group(2);
                            StringBuilder newVal = new StringBuilder("{{this.id}}.").append(method);
                            
                            if (args != null && !args.isBlank()) {
                                newVal.append("(");
                                String[] splitArgs = args.split(",");
                                for (int i = 0; i < splitArgs.length; i++) {
                                    String arg = splitArgs[i].trim();
                                    if (!arg.startsWith("'") && !arg.startsWith("\"") && 
                                        !arg.matches("-?\\d+(\\.\\d+)?") && !loopAliases.contains(arg) &&
                                        !arg.equals("this") && !arg.equals("row") && 
                                        !arg.equals("true") && !arg.equals("false")) {
                                        newVal.append("{{this.id}}.").append(arg);
                                    } else { newVal.append(arg); }
                                    if (i < splitArgs.length - 1) newVal.append(",");
                                }
                                newVal.append(")");
                            }
                            
                            if (key.startsWith("@")) {
                                String eventName = key.substring(1); 
                                toAdd.add(new Attribute("data-call-" + eventName, newVal.toString()));
                                toRemove.add(key);
                            } else {
                                attr.setValue(newVal.toString());
                            }
                        }
                    }
                }
                toRemove.forEach(el::removeAttr);
                toAdd.forEach(a -> el.attr(a.getKey(), a.getValue()));
            }
            
            return doc.html();
        } catch (Throwable e) { 
        	processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, 
                    "⚠️ [AOT] Jsoup no disponible o falló (" + e.getClass().getSimpleName() + "). Usando fallback Regex. Error: " + e.getMessage());
            return html;
        }
    }
    
    
    
    private void generateResolvePathHelper(Writer w) throws IOException {
        w.write("\n    /** Helper AOT para resolver rutas profundas en alias de bucles */\n");
        w.write("    private Object resolvePath(Object obj, String path) {\n");
        w.write("        if (obj == null || path == null || path.isEmpty()) return obj;\n");
        w.write("        try {\n");
        w.write("            Object current = obj;\n");
        w.write("            for (String part : path.split(\"\\\\.\")) {\n");
        w.write("                if (current == null) return null;\n");
        w.write("                java.lang.reflect.Field f = findField(current.getClass(), part);\n");
        w.write("                if (f != null) {\n");
        w.write("                    f.setAccessible(true);\n");
        w.write("                    current = f.get(current);\n");
        w.write("                } else {\n");
        w.write("                    java.lang.reflect.Method m = findGetter(current.getClass(), part);\n");
        w.write("                    if (m != null) {\n");
        w.write("                        m.setAccessible(true);\n");
        w.write("                        current = m.invoke(current);\n");
        w.write("                    } else return null;\n");
        w.write("                }\n");
        w.write("            }\n");
        w.write("            return current;\n");
        w.write("        } catch (Exception e) { return null; }\n");
        w.write("    }\n");

        w.write("\n    private java.lang.reflect.Field findField(Class<?> c, String n) {\n");
        w.write("        try { return c.getDeclaredField(n); } catch (Exception e) {\n");
        w.write("            return (c.getSuperclass() != null) ? findField(c.getSuperclass(), n) : null;\n");
        w.write("        }\n");
        w.write("    }\n");

        w.write("\n    private java.lang.reflect.Method findGetter(Class<?> c, String n) {\n");
        w.write("        String g = \"get\" + n.substring(0,1).toUpperCase() + n.substring(1);\n");
        w.write("        try { return c.getMethod(g); } catch (Exception e) {\n");
        // Soporte para Records y métodos con el mismo nombre de la propiedad
        w.write("            try { return c.getMethod(n); } catch (Exception e2) { return null; }\n");
        w.write("        }\n");
        w.write("    }\n");
    }
    
    
    
    
    
    
    
 // =========================================================
    // 🔥 ESTRUCTURAS DE NODOS (ACTUALIZADAS PARA RECURSIVIDAD)
    // =========================================================

    // 1. Interfaz Node mejorada: acepta el nombre del buffer ("sb", "_sbSlot", etc.)
    interface Node { 
        void generate(Writer w, Set<String> aliases, String sbName) throws IOException; 
    }

    // 2. Texto plano
    record TextNode_(String content) implements Node {
        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            if (content.isEmpty()) return;
            w.write("        " + sbName + ".append(\"" + escapeJava(content) + "\");\n");
        }
    }

    // 3. Variables {{...}}
    record VarNode(String path) implements Node {
        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            if (path.equals("this.id")) {
                w.write("        " + sbName + ".append(t.getId());\n");
                return;
            }
            String root = path.split("\\.")[0];
            if (aliases.contains(root)) {
                 String sub = path.contains(".") ? path.substring(root.length() + 1) : "";
                 if(!sub.isEmpty()) 
                     w.write("        " + sbName + ".append(java.util.Objects.toString(resolvePath(" + root + ", \"" + sub + "\"), \"\"));\n");
                 else 
                     w.write("        " + sbName + ".append(java.util.Objects.toString(" + root + ", \"\"));\n");
            } else {
                w.write("        " + sbName + ".append(\"{{\").append(t.getId()).append(\"." + path + "}}\");\n");
            }
        }
    }

    // 4. Slot (Inyección del contenido del padre)
    record SlotNode() implements Node {
        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            w.write("        " + sbName + ".append(t._getSlotHtml());\n");
        }
    }



 // =========================================================
    // 🔥 FIX DEFINITIVO: Regex estándar para atributos HTML
    // =========================================================
    record ComponentNode(String name, String attrs, List<Node> slotChildren, int uid) implements Node {
        
        // Grupo 1: Nombre (ej: :greet, ref)
        // Grupo 2: Valor "..." (sin comillas)
        // Grupo 3: Valor '...' (sin comillas)
        // Grupo 4: Valor sin comillas
        private static final Pattern ATTR_REGEX = Pattern.compile(
            "([:a-zA-Z0-9_@-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\"'\\s>]+))"
        );

        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            String ATTRS = "_attrs_" + uid;
            String SLOT  = "_slotContent_" + uid;
            String SBS   = "_sbSlot_" + uid;

            w.write("        {\n");
            w.write("            java.util.Map<String, String> " + ATTRS + " = new java.util.HashMap<>();\n");

            if (attrs != null && !attrs.isBlank()) {
                Matcher m = ATTR_REGEX.matcher(attrs);

                while (m.find()) {
                    String attrName = m.group(1);
                    // Obtener valor limpio de cualquiera de los grupos
                    String attrVal = m.group(2) != null ? m.group(2) : 
                                     m.group(3) != null ? m.group(3) : 
                                     m.group(4);

                    if (attrVal == null) attrVal = "";

                    String processedVal = escapeJava(attrVal); // Ya viene sin comillas

                    boolean isBinding = attrName.startsWith(":");
                    boolean hasInterpolation = attrVal.contains("{{");

                    if (isBinding || hasInterpolation) {
                        String realAttrName = isBinding ? attrName.substring(1) : attrName;
                        if (isBinding) attrName = realAttrName;

                        String coreExpr = attrVal.replace("{{", "").replace("}}", "").trim();
                        String root = coreExpr.split("\\.")[0].replace("!", "");

                        if (!aliases.contains(root) && !isLiteral(coreExpr)) {
                            if (isBinding) {
                                // Binding dinámico: t.getId() + ".variable"
                                w.write("            " + ATTRS + ".put(\"" + escapeJava(attrName) + "\", t.getId() + \"." + processedVal + "\");\n");
                                continue;
                            }
                        }
                    }

                    // Valor estático
                    w.write("            " + ATTRS + ".put(\"" + escapeJava(attrName) + "\", \"" + processedVal + "\");\n");
                }
            }

            // Inyectar ref automático si falta
            w.write("            if (!" + ATTRS + ".containsKey(\"ref\")) " + ATTRS + ".put(\"ref\", t.getId() + \"__aot_\" + (__jrxRefSeq++));\n");

            // Generar Slots
            w.write("            String " + SLOT + " = \"\";\n");
            if (!slotChildren.isEmpty()) {
                w.write("            {\n");
                w.write("                StringBuilder " + SBS + " = new StringBuilder();\n");
                for (Node child : slotChildren) {
                    child.generate(w, aliases, SBS);
                }
                w.write("                " + SLOT + " = " + SBS + ".toString();\n");
                w.write("            }\n");
            }

            w.write("            " + sbName + ".append(t.renderChild(\"" + name + "\", " + ATTRS + ", " + SLOT + "));\n");
            w.write("        }\n");
        }

        private static boolean isLiteral(String s) {
            return s.equals("true") || s.equals("false") || s.equals("null") ||
                   s.matches("-?\\d+(\\.\\d+)?") || s.startsWith("'") || s.startsWith("\"");
        }
        
        // Helper para escapar strings en código Java
        private static String escapeJava(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }


        // Helper para no romper literales
        
    

    // 6. IF Node
    record IfNode(String condition, List<Node> children, List<Node> elseChildren) implements Node {
        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            w.write("        if (java.lang.Boolean.TRUE.equals(this.read(t, \"" + condition + "\"))) {\n");
            for (Node n : children) n.generate(w, aliases, sbName);
            if (!elseChildren.isEmpty()) {
                w.write("        } else {\n");
                for (Node n : elseChildren) n.generate(w, aliases, sbName);
            }
            w.write("        }\n");
        }
    }

    // 7. EACH Node
    record EachNode(String listPath, String alias, List<Node> children) implements Node {
        public void generate(Writer w, Set<String> aliases, String sbName) throws IOException {
            w.write("        Object _list_" + alias + " = this.read(t, \"" + listPath + "\");\n");
            w.write("        if (_list_" + alias + " instanceof java.lang.Iterable<?> _it_" + alias + ") {\n");
            w.write("            for (Object " + alias + " : _it_" + alias + ") {\n");
            Set<String> sub = new HashSet<>(aliases); sub.add(alias);
            for (Node n : children) n.generate(w, sub, sbName);
            w.write("            }\n        }\n");
        }
    }    
    private List<Node> parseToNodes(String html) {
    	if (html == null) html = "";
    	
        List<Node> rootNodes = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(html);
        int lastIdx = 0;
        
        java.util.Stack<List<Node>> listStack = new java.util.Stack<>();
        java.util.Stack<Node> blockStack = new java.util.Stack<>();
        List<Node> currentList = rootNodes;

        while (m.find()) {
            // Texto estático antes del match
            String staticText = html.substring(lastIdx, m.start());
            if (!staticText.isEmpty()) currentList.add(new TextNode_(staticText));

            String tag = m.group(1);      // #if, #each
            String varPath = m.group(3);  // {{variable}}
            String compName = m.group(4); // <Componente ...
            String compAttrs = m.group(5);// atributos
            String compSlot = m.group(6); // slot

            if (varPath != null) {
                currentList.add(new VarNode(varPath));
            } else if (compName != null) {
            	if (compName.equals("slot")) {
                    currentList.add(new SlotNode()); // Usa la lógica de inyección
                } else {
                	List<Node> childrenNodes = parseToNodes(compSlot);
                	currentList.add(new ComponentNode(compName, compAttrs, childrenNodes, __nodeSeq++));
                    //currentList.add(new ComponentNode(compName, compAttrs, compSlot)); // Usa la lógica de instanciación
                }
            } else if (tag != null) {
                // Lógica de bloques (igual que antes)
                if (tag.equals("#if")) {
                    List<Node> sub = new ArrayList<>();
                    IfNode node = new IfNode(m.group(2).trim(), sub, new ArrayList<>());
                    currentList.add(node); listStack.push(currentList); blockStack.push(node); currentList = sub;
                } else if (tag.equals("#each")) {
                    String[] parts = m.group(2).split(" as ");
                    List<Node> sub = new ArrayList<>();
                    EachNode node = new EachNode(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "it", sub);
                    currentList.add(node); listStack.push(currentList); blockStack.push(node); currentList = sub;
                } else if (tag.equals("else")) {
                    if (!blockStack.isEmpty() && blockStack.peek() instanceof IfNode n) currentList = n.elseChildren();
                } else if (tag.startsWith("/")) {
                    if (!listStack.isEmpty()) { currentList = listStack.pop(); blockStack.pop(); }
                }
            }
            lastIdx = m.end();
        }
        // Texto restante
        if (lastIdx < html.length()) currentList.add(new TextNode_(html.substring(lastIdx)));
        return rootNodes;
    }
    
    private ExecutableElement findTemplateMethod(TypeElement clazz) {
        return clazz.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> m.getSimpleName().contentEquals("template"))
                .filter(m -> m.getParameters().isEmpty())
                .findFirst()
                .orElse(null);
    } 
    
    
 // En TemplateProcessor.java (pégalo al final, antes del último cierre de llave)

    private void extractRootsFromExpr(String expr, Set<String> targetVars) {
        if (expr == null || expr.isBlank()) return;
        
        // 1. Separar por cualquier cosa que NO sea carácter de palabra o punto
        // Esto rompe "disabled || loading" en ["disabled", "loading"]
        // Esto rompe "!loading" en ["loading"]
        String[] tokens = expr.split("[^\\w.]+");
        
        for (String t : tokens) {
            if (t.isBlank()) continue;
            
            // Ignorar literales
            if (t.matches("^[0-9].*")) continue; // Empieza con número
            if (t.equals("true") || t.equals("false") || t.equals("null")) continue;
            if (t.equals("this")) continue;

            // Extraer raíz (ej: "user.name" -> "user")
            String root = t.contains(".") ? t.substring(0, t.indexOf('.')) : t;
            targetVars.add(root);
        }
    }
	
}