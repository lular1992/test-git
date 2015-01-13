package com.urjc.sonar;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.github.core.SearchRepository;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class ProbandoAPI {

	// PMD
	private static String pathEjecutablePMD = "F:\\Programas\\pmd-bin-5.1.0\\bin\\pmd.bat";
	private static String pathRulesetPMDComplejidad = "F:\\Programas\\pmd-bin-5.1.0\\rulesets\\rulesetTodaComplejidad.xml";
	private static String pathRulesetPMDLineasCodigo = "F:\\Programas\\pmd-bin-5.1.0\\rulesets\\rulesetTodaLineasClase.xml";
	private static String pathInformeGenerado = "F:\\prueba\\informes\\";
	private static String formatoInforme = "csv";

	// GIT
	private static File pathRepositorioEnLocal;
	private static String raizProyecto = "F:/prueba";
	private static long TAMANIO_MAX_CLONAR = 150000;

	// GITHUB
	private static String pathListaRepositorios = "F:\\prueba\\resultadoBusqueda.txt";

	// LOGS
	private static PrintWriter logBorradoFicheros, log, logProyectosAnalizados,
			datosProyectos, proyectosGrandesNoClonados;

	private static String pathLogBorrados = "F:\\prueba\\logs\\logBorradoFicheros.txt";
	private static String pathLog = "F:\\prueba\\logs\\errores.txt";
	private static String pathVersiones = "F:\\prueba\\informes\\0-ultimoPush.txt";
	private static String pathProyectosAnalizados = "F:\\prueba\\logs\\logProyectosAnalizados.txt";
	private static String pathProyectosGrandesNoClonados = "F:\\prueba\\logs\\logProyectosGrandesNoClonados.txt";

	/**
	 * @author Ana Los parametros de busqueda se introducen por parejas: ej
	 *         language, java La api solo devuelve los 1000 primeros resultados,
	 *         y lo hace de 100 en 100. http://developer.github.com/v3/search/
	 */
	public static List<SearchRepository> buscarRepositorios(
			Map<String, String> parametrosBusqueda) {

		RepositoryService service = new RepositoryService();

		// usuario y contrase�a de github
		service.getClient().setCredentials("XXX", "XXX");

		List<SearchRepository> resultadoBusqueda = null;
		try {
			resultadoBusqueda = service.searchRepositories(parametrosBusqueda);
			// La api de github solo muestra los 1000 primeros resultados
			// Y los devuelve de 100 en 100
			// 20-feb-2014
			for (int i = 2; i < 11; i++) {
				List<SearchRepository> masResultados = service
						.searchRepositories(parametrosBusqueda, i);
				resultadoBusqueda.addAll(masResultados);
			}

		} catch (IOException e) {
			log.println("Error a la hora de buscar los repositorios con parametros "
					+ parametrosBusqueda);
			log.println(e.getMessage());
		}

		return resultadoBusqueda;
	}

	public static void mostrarProyectosBuscados(
			List<SearchRepository> resultadoBusqueda) {
		System.out.println("Num proyectos " + resultadoBusqueda.size());

		for (SearchRepository repo : resultadoBusqueda) {
			System.out.println("Nombre: " + repo.getName() + " Url: "
					+ repo.getUrl() + ".git");
		}
	}

	public static Process ejecutarComandoPMD(String comandoAEjecutar,
			String tipo) {

		Runtime runtime = Runtime.getRuntime();
		Process p1 = null;
		try {
			p1 = runtime.exec(comandoAEjecutar);
		} catch (IOException e) {
			log.println("No se ha podido ejecutar la instrucci�n PMD de "
					+ tipo);
			log.println(e.getMessage());
		} catch (Exception e) {
			log.println("Error desconocido. Comando ejecutado: "
					+ comandoAEjecutar);
			log.println(e.getMessage());
		}

		return p1;

	}

	public static void borrarDirectorio(String path) {

		logBorradoFicheros.write("---Eliminando " + path + "\n\n");

		boolean eliminado = (new EliminarRecursivamente()).invokeDelete(path,
				logBorradoFicheros);

		logBorradoFicheros.write("---Terminado eliminar " + path + "\n\n\n");

		if (eliminado) {
			log.write("Eliminado todo " + path + "\n");
			logProyectosAnalizados.write("BORRADO\n");
		} else {
			log.write("No se ha podido eliminar todo el directorio " + path
					+ "\n");
		}
	}

	public static List<SearchRepository> recuperarLista(String ruta) {
		InputStream archivoListaRepositorios;
		List<SearchRepository> listaRecuperada = null;

		try {
			archivoListaRepositorios = new FileInputStream(ruta);
			InputStream buffer = new BufferedInputStream(
					archivoListaRepositorios);
			ObjectInput input = new ObjectInputStream(buffer);
			// deserializar la lista
			listaRecuperada = (List<SearchRepository>) input.readObject();
			input.close();
		} catch (FileNotFoundException e) {
			log.println("No se ha encontrado el archivo que guarda la lista de repositorios.");
			log.println(e.getMessage());
		} catch (IOException e) {
			log.println("Fallo al crear Object Input Stream");
			log.println(e.getMessage());
		} catch (ClassNotFoundException e) {
			log.println("El tipo del fichero es distinto al tipo de la clase donde se quiere guardar");
			log.println(e.getMessage());
		}

		return listaRecuperada;
	}

	public static void guardarLista(List<SearchRepository> resultadoBusqueda,
			String ruta) throws IOException {
		// serializar la lista
		OutputStream file = new FileOutputStream(ruta);
		OutputStream buffer = new BufferedOutputStream(file);
		ObjectOutput output = new ObjectOutputStream(buffer);
		output.writeObject(resultadoBusqueda);
		output.close();
	}

	public static int buscarIndexPorNombre(
			List<SearchRepository> listaRepositorios, String nombre) {

		int i = 0;
		boolean encontrado = false;
		while (!encontrado && i < listaRepositorios.size()) {
			encontrado = listaRepositorios.get(i).getName().equals(nombre);
			i++;
		}

		if (i >= listaRepositorios.size()) {
			return -1;
		} else {
			return i - 1;
		}

	}

	public static Process clonarRepositorio(SearchRepository proyecto) {

		String urlProyecto = proyecto.getUrl() + ".git";
		String comando = "";
		Process p1 = null;

		try {
			pathRepositorioEnLocal = File.createTempFile(proyecto.getName(),
					"", new File(raizProyecto));
			pathRepositorioEnLocal.delete();

			// Clonar
			log.println("Clonando desde " + urlProyecto + " a "
					+ pathRepositorioEnLocal + "\n");

			comando = "git clone " + urlProyecto + " " + pathRepositorioEnLocal;

			Runtime runtime = Runtime.getRuntime();
			p1 = runtime.exec(comando);
		} catch (IOException e) {
			log.println("No se ha podido clonar el repositorio "
					+ proyecto.getName() + " desde " + urlProyecto);
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
		} catch (Exception e) {
			log.println("Error desconocido. Comando ejecutado: " + comando);
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
		}

		return p1;

	}

	public static int clonarRepositorioJGit(SearchRepository proyecto) {
		// Preparar una carpeta para el repositorio a clonar

		int exit = 0;
		String urlProyecto = proyecto.getUrl() + ".git";
		try {
			pathRepositorioEnLocal = File.createTempFile(proyecto.getName(),
					"", new File(raizProyecto));
			pathRepositorioEnLocal.delete();

			// Clonar
			log.println("Clonando desde " + urlProyecto + " a "
					+ pathRepositorioEnLocal + "\n");
			Git git = Git.cloneRepository().setURI(urlProyecto)
					.setDirectory(pathRepositorioEnLocal).call();

			git.close();

			logProyectosAnalizados.println("CLONADO\n");
		} catch (IOException e) {
			log.println("ERROR No se ha podido clonar el repositorio "
					+ proyecto.getName() + " " + urlProyecto);
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
			exit = 1;
		} catch (InvalidRemoteException e) {
			log.println("ERROR Url " + urlProyecto + " no valida.");
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
			exit = 1;
		} catch (TransportException e) {
			log.println("ERROR Transport exception.");
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
			exit = 1;
		} catch (GitAPIException e) {
			log.println("ERROR Api de git");
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
			exit = 1;
		} catch (Exception e) {
			log.println("ERROR desconocido");
			log.println("Url proyecto " + urlProyecto);
			log.println(e.getMessage());
			logProyectosAnalizados.println("NO CLONADO");
			exit = 1;
		}

		return exit;

	}

	public static void guardarDatosGitDeTodosProyectos(
			List<SearchRepository> resultadosBusqueda) {

		for (int i = 0; i < resultadosBusqueda.size(); i++) {
			SearchRepository proyecto = resultadosBusqueda.get(i);
			datosProyectos
					.println("Nombre, descripci�n, url, homepage, language, owner, pushed at, created at, forks, open issues,watchers, tama�o, tiene descargas?, tiene wiki?");
			datosProyectos.println(proyecto.getName());
			datosProyectos.println(proyecto.getDescription());
			datosProyectos.println(proyecto.getUrl());
			datosProyectos.println(proyecto.getHomepage());
			datosProyectos.println(proyecto.getLanguage());
			datosProyectos.println(proyecto.getOwner());
			datosProyectos.println(proyecto.getPushedAt());
			datosProyectos.println(proyecto.getCreatedAt());
			datosProyectos.println(proyecto.getForks());
			datosProyectos.println(proyecto.getOpenIssues());
			datosProyectos.println(proyecto.getWatchers());
			datosProyectos.println(proyecto.getSize());
			datosProyectos.println(proyecto.isHasDownloads());
			datosProyectos.println(proyecto.isHasWiki());
		}
	}

	public static void bigdataDesdeIndex(int indice, int fin,
			List<SearchRepository> resultadoBusqueda) {

		for (int i = indice; i < fin; i++) {

			SearchRepository proyecto = resultadoBusqueda.get(i);
			log.println("-----" + proyecto.getName() + "-----\n\n");
			logProyectosAnalizados.println(i + " PROYECTO "
					+ proyecto.getName() + "\n\n");
			long tamanio = proyecto.getSize();

			if (tamanio > TAMANIO_MAX_CLONAR) {
				proyectosGrandesNoClonados
						.println(i + " " + proyecto.getName());
				logProyectosAnalizados.println("Muy grande," + tamanio
						+ " dejado para clonar con git.\n");
				log.println("Muy grande," + tamanio
						+ " dejado para clonar con git.\n");
			} else {

				// Clonar el repositorio
				Process clonarRepo = clonarRepositorio(proyecto);
				int exitStatusClonar;
				try {
					exitStatusClonar = clonarRepo.waitFor();
					if (exitStatusClonar == 0) {

						log.println("El proyecto " + proyecto.getName()
								+ " se ha clonado con exito.");
						logProyectosAnalizados.println("CLONADO\n");

						// Pasarle pmd
						String comandoPMDComplejidad = pathEjecutablePMD
								+ " -dir "
								+ pathRepositorioEnLocal.getAbsolutePath()
								+ " -f " + formatoInforme + " -R "
								+ pathRulesetPMDComplejidad + ">  "
								+ pathInformeGenerado
								+ pathRepositorioEnLocal.getName()
								+ "-complejidad-ciclo" + "." + formatoInforme;

						Process pmd = ejecutarComandoPMD(comandoPMDComplejidad,
								"complejidad ciclom�tica");

						String comandoPMDLoc = pathEjecutablePMD + " -dir "
								+ pathRepositorioEnLocal.getAbsolutePath()
								+ " -f " + formatoInforme + " -R "
								+ pathRulesetPMDLineasCodigo + ">  "
								+ pathInformeGenerado
								+ pathRepositorioEnLocal.getName()
								+ "-lineas-codigo" + "." + formatoInforme;

						Process lineasCodigo = ejecutarComandoPMD(
								comandoPMDLoc, "l�neas de c�digo");
						

						// Esperar a que termine
						try {
							final int exitStatus = pmd.waitFor();
							final int exitStatusLineas = lineasCodigo.waitFor();

							if (exitStatus == 0) {
								log.println("El informe de complejidad ciclomatica "
										+ pathRepositorioEnLocal.getName()
										+ " se ha creado con exito.\n\n");
								logProyectosAnalizados
										.println("ANALIZADA COMPLEJIDAD CICLOMATICA\n");

							} else {
								log.println("El informe de complejidad ciclomatica "
										+ pathRepositorioEnLocal.getName()
										+ " no se ha generado.\n\n");
								logProyectosAnalizados
										.println("NO ANALIZADA COMPLEJIDAD CICLOMATICA\n");
							}

							if (exitStatusLineas == 0) {
								log.println("El informe lineas de codigo de "
										+ pathRepositorioEnLocal.getName()
										+ " se ha creado con exito.\n\n");
								logProyectosAnalizados
										.println("ANALIZADAS LINEAS CODIGO\n");
							} else {
								log.println("El informe de lineas de codigo"
										+ pathRepositorioEnLocal.getName()
										+ " no se ha generado.\n\n");
								logProyectosAnalizados
										.println("NO ANALIZADA LINEAS DE CODIGO\n");
							}

							// Borrar el directorio
							borrarDirectorio(pathRepositorioEnLocal
									.getAbsolutePath());

							log.write("----- An�lisis de "
									+ pathRepositorioEnLocal
									+ " terminado. -----\n\n\n");
							logProyectosAnalizados.write("----\n\n");
						} catch (InterruptedException e) {
							log.println("ERROR proceso PMD interrumpido");
							log.println(e.getMessage());
						}
					}
				} catch (InterruptedException e1) {
					log.println("ERROR proceso GIT interrumpido");
					log.println(e1.getMessage());
				}

			}
		}
	}

	public static PrintWriter crearLog(String pathArchivo) {
		PrintWriter out = null;
		File archivoLog = new File(pathArchivo);

		// Si no existe el fichero, crearlo
		if (!archivoLog.exists()) {
			try {
				archivoLog.createNewFile();
			} catch (IOException e) {
				System.err.println("No se ha podido crear el fichero "
						+ archivoLog.getName() + ".");
				System.err.println(e.getMessage());
			}
		}

		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(
					pathArchivo, true)));
		} catch (IOException e) {
			System.err.println("No se ha podido crear el PrintWriter "
					+ archivoLog.getName() + ".");
			System.err.println(e.getMessage());
		}

		return out;
	}

	public static void main(String[] args) {

		try {

			// Crear los logs
			logBorradoFicheros = crearLog(pathLogBorrados);
			log = crearLog(pathLog);
			logProyectosAnalizados = crearLog(pathProyectosAnalizados);
			datosProyectos = crearLog(pathVersiones);
			proyectosGrandesNoClonados = crearLog(pathProyectosGrandesNoClonados);

			// Map<String, String> parametrosBusqueda = new HashMap<String,
			// String>();
			// parametrosBusqueda.put("language", "java");
			// parametrosBusqueda.put("sort", "forks");
			//
			// List<SearchRepository> resultadoBusqueda =
			// buscarRepositorios(parametrosBusqueda);

			// guardarLista(resultadoBusqueda,pathListaRepositorios);
			// guardarDatosGitDeTodosProyectos(resultadoBusqueda);

			List<SearchRepository> resultadoBusqueda = recuperarLista(pathListaRepositorios);

			// bigdataDesdeIndex(0,resultadoBusqueda.size(), resultadoBusqueda);

			bigdataDesdeIndex(41, 42, resultadoBusqueda);

		} finally {
			logBorradoFicheros.close();
			log.close();
			logProyectosAnalizados.close();
			datosProyectos.close();
			proyectosGrandesNoClonados.close();
		}

	}

}
