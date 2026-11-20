package com.ejemplo.servlet;

import com.ejemplo.model.Tarea;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@WebServlet(name = "TareasServlet", urlPatterns = {"/tareas"})
public class TareasServlet extends HttpServlet {

    /*
     * La lista es una variable de instancia porque es estado COMPARTIDO de
     * toda la aplicacion (todas las peticiones deben ver la misma lista), no
     * un dato de una peticion individual. Los datos propios de cada peticion
     * (titulo, id, filtros) se manejan siempre como variables locales.
     * Como el Servlet tiene una sola instancia usada por varios hilos, se
     * usan CopyOnWriteArrayList y AtomicInteger para que agregar/eliminar y
     * generar ids sean seguros ante peticiones concurrentes.
     */
    private final List<Tarea> tareas = new CopyOnWriteArrayList<>();
    private final AtomicInteger contadorId = new AtomicInteger(1);

    @Override
    public void init() throws ServletException {
        Date hoy = new Date();
        tareas.add(new Tarea(contadorId.getAndIncrement(), "Leer documentación de Servlets",
            "Estudio", "Alta", sumarDias(hoy, 2)));
        tareas.add(new Tarea(contadorId.getAndIncrement(), "Implementar ciclo GET/POST",
            "Estudio", "Alta", sumarDias(hoy, 3)));
        tareas.add(new Tarea(contadorId.getAndIncrement(), "Preparar sustentación del laboratorio",
            "Evaluación", "Media", sumarDias(hoy, 7)));
        tareas.add(new Tarea(contadorId.getAndIncrement(), "Revisar JSTL y Expression Language",
            "Estudio", "Baja", sumarDias(hoy, 10)));

        // Se expone la misma lista en el ServletContext (applicationScope)
        // para que DetalleTareaServlet la lea sin duplicar el estado
        getServletContext().setAttribute("tareas", tareas);
    }

    private Date sumarDias(Date base, int dias) {
        long unDiaMs = 24L * 60 * 60 * 1000;
        return new Date(base.getTime() + dias * unDiaMs);
    }

    /** GET /tareas — listar con filtro combinado (texto + categoría + prioridad) */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession();
        boolean hayFiltroEnUrl = req.getParameter("q") != null
            || req.getParameter("cat") != null
            || req.getParameter("prioridad") != null;

        if (hayFiltroEnUrl) {
            // El usuario aplicó un filtro nuevo: se recuerda en la SESIÓN (no en
            // el request) para que sobreviva a las siguientes peticiones, p. ej.
            // al ir a /tareas/detalle y volver al listado
            session.setAttribute("filtroTexto", req.getParameter("q"));
            session.setAttribute("filtroCategoria", req.getParameter("cat"));
            session.setAttribute("filtroPrioridad", req.getParameter("prioridad"));
        }
        // Sin parámetros en la URL (ej. clic en "Volver al listado") se usa
        // el último filtro guardado en la sesión, si existe
        mostrarLista(req, resp);
    }

    /** POST /tareas — agregar, eliminar, completar o identificar usuario */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String accion = req.getParameter("accion");

        if ("agregar".equals(accion)) {
            String titulo = req.getParameter("titulo");
            if (titulo == null || titulo.isBlank()) {
                // Error de validación: se hace forward (no redirect) para
                // conservar el mensaje de error, que vive solo en el request
                req.setAttribute("error", "El título no puede estar vacío");
                mostrarLista(req, resp);
                return;
            }
            // Valores por defecto: el formulario rápido de la Parte 1 solo pide título
            tareas.add(new Tarea(contadorId.getAndIncrement(), titulo.trim(),
                "General", "Media", sumarDias(new Date(), 5)));

        } else if ("eliminar".equals(accion)) {
            int id = leerId(req);
            tareas.removeIf(t -> t.getId() == id);

        } else if ("completar".equals(accion)) {
            int id = leerId(req);
            tareas.stream()
                .filter(t -> t.getId() == id)
                .findFirst()
                .ifPresent(t -> t.setCompletada(true));

        } else if ("identificar".equals(accion)) {
            String nombre = req.getParameter("nombre");
            if (nombre != null && !nombre.isBlank()) {
                req.getSession().setAttribute("usuario", nombre.trim());
            }
        }

        // Patrón PRG: redirigir después de POST (todas las acciones) para que
        // recargar la página no reenvíe el formulario
        resp.sendRedirect(req.getContextPath() + "/tareas");
    }

    /**
     * Aplica el filtro guardado en sesión y hace forward a la vista.
     * La lista filtrada es solo de request: es el resultado calculado
     * para esta respuesta y no tiene sentido conservarlo aparte del filtro.
     */
    private void mostrarLista(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession();
        String filtroTexto     = (String) session.getAttribute("filtroTexto");
        String filtroCategoria = (String) session.getAttribute("filtroCategoria");
        String filtroPrioridad = (String) session.getAttribute("filtroPrioridad");

        // Cada filtro ausente o vacío se ignora, así funciona cualquier
        // combinación de parámetros presentes o ausentes
        List<Tarea> resultado = tareas.stream()
            .filter(t -> filtroTexto == null || filtroTexto.isBlank()
                         || t.getTitulo().toLowerCase().contains(filtroTexto.trim().toLowerCase()))
            .filter(t -> filtroCategoria == null || filtroCategoria.isBlank()
                         || t.getCategoria().equals(filtroCategoria))
            .filter(t -> filtroPrioridad == null || filtroPrioridad.isBlank()
                         || t.getPrioridad().equals(filtroPrioridad))
            .collect(Collectors.toList());

        List<String> categorias = tareas.stream()
            .map(Tarea::getCategoria).distinct().sorted()
            .collect(Collectors.toList());

        req.setAttribute("tareas", resultado);
        req.setAttribute("categorias", categorias);
        req.setAttribute("filtroTexto", filtroTexto);
        req.setAttribute("filtroCategoria", filtroCategoria);
        req.setAttribute("filtroPrioridad", filtroPrioridad);
        req.getRequestDispatcher("/WEB-INF/views/tareas.jsp")
           .forward(req, resp);
    }

    /** Lee el parámetro id; si no es un número válido devuelve -1 (no coincide con ninguna tarea). */
    private int leerId(HttpServletRequest req) {
        try {
            return Integer.parseInt(req.getParameter("id"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
