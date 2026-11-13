package com.elegant.software.blitzpay

import com.tngtech.archunit.core.domain.JavaClass
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModule
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.core.DependencyType
import java.io.File

/**
 * Renders the Spring Modulith design (module graph, events, module canvases) as a static
 * Mermaid page in build/architecture-site. Published to GitHub Pages by architecture-docs.yml.
 */
internal class ArchitectureSiteTest {

    private val modules = ApplicationModules.of(QuickpayApplication::class.java)
    private val all = modules.toList().sortedBy { it.identifier.toString() }

    @Test
    fun writeArchitectureSite() {
        val out = File("build/architecture-site").apply { mkdirs() }
        File(out, "index.html").writeText(page())
    }

    private fun id(m: ApplicationModule) = m.identifier.toString().replace(Regex("[^A-Za-z0-9]"), "_")
    private fun name(m: ApplicationModule) = m.identifier.toString()

    /** Direct outgoing edges: target module -> dependency types used. */
    private fun edges(m: ApplicationModule): Map<ApplicationModule, Set<DependencyType>> =
        m.getDirectDependencies(modules).stream().toList()
            .groupBy({ it.targetModule }, { it.dependencyType })
            .filterKeys { it != m }
            .mapValues { it.value.toSortedSet() }

    private fun edgeLine(from: ApplicationModule, to: ApplicationModule, types: Set<DependencyType>): String {
        val label = types.joinToString(", ") { it.name.lowercase() }
        // Event-only coupling is the preferred style; draw it dashed so direct coupling stands out.
        val arrow = if (types == setOf(DependencyType.EVENT_LISTENER)) "-.->" else "-->"
        return "  ${id(from)} $arrow|$label| ${id(to)}"
    }

    private fun node(m: ApplicationModule) = "  ${id(m)}[\"${name(m)}\"]"

    private fun overview() = buildString {
        appendLine("flowchart LR")
        all.forEach { appendLine(node(it)) }
        all.forEach { m -> edges(m).forEach { (t, types) -> appendLine(edgeLine(m, t, types)) } }
        all.forEach { appendLine("  click ${id(it)} \"#${id(it)}\"") }
    }

    private fun neighbourhood(m: ApplicationModule) = buildString {
        val out = edges(m)
        val incoming = all.filter { it != m }.mapNotNull { o -> edges(o)[m]?.let { o to it } }
        appendLine("flowchart LR")
        (listOf(m) + out.keys + incoming.map { it.first }).distinct().forEach { appendLine(node(it)) }
        appendLine("  style ${id(m)} stroke-width:3px")
        out.forEach { (t, types) -> appendLine(edgeLine(m, t, types)) }
        incoming.forEach { (o, types) -> appendLine(edgeLine(o, m, types)) }
    }

    private fun canvas(m: ApplicationModule): String {
        fun list(title: String, items: List<String>) =
            if (items.isEmpty()) "" else "<h4>$title</h4><ul>${items.joinToString("") { "<li><code>${esc(it)}</code></li>" }}</ul>"
        fun simple(c: JavaClass) = c.name.removePrefix(m.basePackage.name + ".")
        val beans = m.springBeans.sortedBy { it.fullyQualifiedTypeName }
        val named = m.namedInterfaces.stream().toList().filter { it.isNamed }
            .map { ni -> "${ni.name}: ${ni.asJavaClasses().toList().joinToString { simple(it) }}" }
        return """
            <section id="${id(m)}">
              <h2>${esc(m.displayName)} <small><code>${esc(m.basePackage.name)}</code></small></h2>
              <pre class="mermaid">${esc(neighbourhood(m))}</pre>
              ${list("Published events", m.publishedEvents.map { simple(it.type) })}
              ${list("Events listened to", m.getEventsListenedTo(modules).map { it.name })}
              ${list("Named interfaces", named)}
              ${list("API beans", beans.filter { it.isApiBean }.map { simple(it.type) })}
              ${list("Internal beans", beans.filterNot { it.isApiBean }.map { simple(it.type) })}
              ${list("Aggregate roots", m.aggregateRoots.map { simple(it) })}
              ${list("Value types", m.valueTypes.map { simple(it) })}
            </section>
        """.trimIndent()
    }

    private fun page() = """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>BlitzPay Module Design</title>
        <style>
          :root { color-scheme: light dark; --fg:#1f2328; --bg:#fff; --muted:#59636e; --line:#d1d9e0 }
          @media (prefers-color-scheme: dark) { :root { --fg:#e6edf3; --bg:#0d1117; --muted:#9198a1; --line:#3d444d } }
          body { font: 15px/1.5 system-ui, sans-serif; color: var(--fg); background: var(--bg); max-width: 1100px; margin: 0 auto; padding: 16px }
          section { border-top: 1px solid var(--line); padding-top: 8px; margin-top: 24px }
          small, .muted { color: var(--muted) } ul { columns: 2 } pre.mermaid { overflow-x: auto }
        </style></head><body>
        <h1>BlitzPay Module Design</h1>
        <p class="muted">Generated from Spring Modulith <code>ApplicationModules</code> at commit
          <code>${esc(System.getenv("GITHUB_SHA")?.take(7) ?: "local")}</code>. Dashed arrows = event-only coupling.</p>
        <pre class="mermaid">${esc(overview())}</pre>
        ${all.joinToString("\n") { canvas(it) }}
        <script type="module">
          import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs';
          mermaid.initialize({ startOnLoad: true, securityLevel: 'loose',
            theme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'default' });
        </script>
        </body></html>
    """.trimIndent()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
