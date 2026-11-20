# Post-contenido — Unidad 5: Fundamentos de Java Web (Servlets y JSP)

## Descripción
Repositorio del laboratorio de la Unidad 5 de Programación Web —
Séptimo Semestre. Un único proyecto Maven Web (`gestion-tareas`) con dos
partes que extienden el mismo dominio de tareas: la Parte 1 construye
el Servlet base con el ciclo GET/POST y el patrón Post/Redirect/Get; la
Parte 2 le agrega filtrado combinado, vista de detalle con un segundo
Servlet, y sesión para personalización y persistencia del filtro.

## Estructura del repositorio (plana)
Todos los archivos viven en la raíz del repositorio, sin subcarpetas.
El `pom.xml` está configurado para compilar los `.java` desde la raíz y
para armar el WAR con la estructura estándar que espera Tomcat:

| Archivo en la raíz         | Ubicación dentro del WAR        |
|----------------------------|---------------------------------|
| `Tarea.java`               | `WEB-INF/classes/com/ejemplo/model/Tarea.class` |
| `TareasServlet.java`       | `WEB-INF/classes/com/ejemplo/servlet/TareasServlet.class` |
| `DetalleTareaServlet.java` | `WEB-INF/classes/com/ejemplo/servlet/DetalleTareaServlet.class` |
| `web.xml`                  | `WEB-INF/web.xml`               |
| `tareas.jsp`               | `WEB-INF/views/tareas.jsp`      |
| `detalle.jsp`              | `WEB-INF/views/detalle.jsp`     |
| `estilos.css`              | `css/estilos.css`               |
| `index.jsp`                | `index.jsp`                     |

Las vistas quedan dentro de `WEB-INF/views`, así que no se pueden abrir
directamente desde el navegador: solo se llega a ellas a través de los
Servlets (forward).

## Parte 1 — Servlet de gestión de tareas
`TareasServlet` procesa peticiones GET (listar) y POST (agregar,
eliminar), con validación en el servidor (título vacío → mensaje de
error sin agregar la tarea) y el patrón Post/Redirect/Get para evitar el
reenvío de formularios al recargar la página.

## Parte 2 — Filtros, detalle y sesión
`TareasServlet` se extiende con filtrado combinado por texto, categoría
y prioridad, y con las acciones `completar` e `identificar`. Se agrega
`DetalleTareaServlet`, que lee la misma lista de tareas desde
`applicationScope` y hace forward a una vista de detalle con la fecha
límite formateada (`fmt:formatDate`). `HttpSession` guarda el nombre del
usuario identificado y el último filtro aplicado.

## Decisiones de diseño
- La lista de tareas es una variable de instancia porque es estado
  compartido de toda la aplicación, no un dato de una petición
  individual (ver comentario en `TareasServlet`). Los datos propios de
  cada petición (título, id, filtros) son variables locales. Como el
  Servlet tiene una sola instancia usada por varios hilos, la lista es
  un `CopyOnWriteArrayList` y el contador de ids un `AtomicInteger`.
- El filtro activo y el nombre del usuario se guardan en `HttpSession`,
  no en el request, porque deben sobrevivir a varias peticiones
  distintas (navegación entre `/tareas` y `/tareas/detalle`). La lista
  filtrada sí es solo de request: es el resultado calculado para esa
  respuesta.
- `DetalleTareaServlet` obtiene las tareas desde el `ServletContext`
  (`applicationScope`) en vez de duplicar la lista, y usa forward en
  lugar de redirect porque solo necesita entregar un objeto ya
  calculado a una vista, sin generar una nueva petición del navegador.
- Todas las acciones POST terminan en `sendRedirect` (PRG), excepto el
  error de validación, que hace forward para conservar el mensaje de
  error que vive en el request.
- Los estilos se extrajeron a `estilos.css` (publicado como
  `css/estilos.css`) al agregar una segunda vista (`detalle.jsp`), para
  no duplicar el bloque `<style>` de la Parte 1 en cada JSP.
- Los textos que escribe el usuario se muestran con `<c:out>` /
  `fn:escapeXml` para evitar inyección de HTML.
- Se usan las URIs de JSTL 3.0 (`jakarta.tags.core`, `jakarta.tags.fmt`,
  `jakarta.tags.functions`), las propias de Jakarta EE 10.

## Cómo compilar y desplegar
Requisitos: JDK 17+, Maven 3.8+, Apache Tomcat 10.1.x.

1. Clonar el repositorio: `git clone [URL-del-repo]`
2. Ejecutar `mvn clean package` → genera `target/gestion-tareas.war`
3. Opción A: copiar `target/gestion-tareas.war` a la carpeta `webapps`
   de Tomcat e iniciar Tomcat.
   Opción B (IntelliJ): abrir la carpeta como proyecto Maven, Run → Edit
   Configurations → + → Tomcat Server → Local, y en Deployment agregar el
   artefacto `gestion-tareas:war exploded`.
4. Abrir `http://localhost:8080/gestion-tareas/tareas`

## Capturas de pantalla
![Lista de tareas con filtro](captura-01.png)
![Detalle de una tarea](captura-02.png)
