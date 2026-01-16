package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

// 👇 IMPORTS ESTÁTICOS VITALES
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses; // Arregla el error de noClasses()
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo; // Arregla assignableTo

@AnalyzeClasses(packages = "com.ciro", importOptions = ImportOption.DoNotIncludeTests.class)
public class JReactiveRulesTest {

    // --------------------------------------------------------------------------------
    // 🧱 CONDICIÓN PERSONALIZADA (Para arreglar el error de haveConstructor)
    // --------------------------------------------------------------------------------
    static final ArchCondition<JavaClass> HAVE_NO_ARG_CONSTRUCTOR =
            new ArchCondition<JavaClass>("tener un constructor vacío") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    // tryGetConstructor() sin args busca el constructor por defecto
                    boolean hasNoArg = item.tryGetConstructor().isPresent();
                    if (!hasNoArg) {
                        String msg = item.getName() + " no tiene constructor vacío (necesario para reflection)";
                        events.add(SimpleConditionEvent.violated(item, msg));
                    }
                }
            };

    // --------------------------------------------------------------------------------
    // 📏 REGLAS
    // --------------------------------------------------------------------------------

    // 1. Componentes deben ser Serializables
    @ArchTest
    static final ArchRule components_must_be_serializable = classes()
            .that().areAssignableTo(HtmlComponent.class)
            .should().implement(Serializable.class)
            .because("El estado viaja a Redis.");

    // 2. Campos @State seguros (FIX: usaba beAssignableTo incorrectamente)
    @ArchTest
    static final ArchRule state_fields_must_be_safe = fields()
            .that().areAnnotatedWith(State.class)
            // 🔥 CORRECCIÓN: Usar haveRawType(assignableTo(...))
            .should().haveRawType(assignableTo(Serializable.class))
            .orShould().haveRawType(assignableTo(Collection.class))
            .orShould().haveRawType(assignableTo(Map.class))
            .orShould().haveRawType(boolean.class).orShould().haveRawType(int.class)
            .orShould().haveRawType(long.class).orShould().haveRawType(double.class)
            .because("Todo @State debe ser serializable.");

    // 3. Objetos vivos deben ser transient
    @ArchTest
    static final ArchRule runtime_objects_must_be_transient = fields()
            .that().haveRawType(java.util.Timer.class)
            .or().haveRawType(Thread.class)
            .or().haveRawType(Runnable.class)
            .should().haveModifier(JavaModifier.TRANSIENT)
            .because("Objetos vivos no sobreviven a la serialización.")
            .allowEmptyShould(true);;

    // 4. Métodos @Call públicos
    @ArchTest
    static final ArchRule calls_must_be_public = methods()
            .that().areAnnotatedWith(Call.class)
            .should().bePublic()
            .because("El framework invoca estos métodos desde el exterior.");

    // 5. Constructor vacío (FIX: Usamos la condición personalizada de arriba)
    @ArchTest
    static final ArchRule components_must_have_no_arg_constructor = classes()
            .that().areAssignableTo(HtmlComponent.class)
            .and().doNotHaveModifier(JavaModifier.ABSTRACT)
            // 🔥 CORRECCIÓN: Usamos should(Condicion)
            .should(HAVE_NO_ARG_CONSTRUCTOR)
            .because("DefaultComponentFactory requiere un constructor vacío.");

    // 6. @State no debe ser final
    @ArchTest
    static final ArchRule state_fields_must_not_be_final = fields()
            .that().areAnnotatedWith(State.class)
            .should().notHaveModifier(JavaModifier.FINAL)
            .because("El framework necesita inyectar proxies.");

    
    
}