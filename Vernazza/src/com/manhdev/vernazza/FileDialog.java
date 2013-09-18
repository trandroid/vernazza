package com.manhdev.vernazza;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/**
 * Activity para escolha de arquivos/diretorios.
 * 
 * @author android
 * 
 */
public class FileDialog extends ListActivity {

	/**
	 * Chave de um item da lista de paths.
	 */
	private static final String ITEM_KEY = "key";

	/**
	 * Imagem de um item da lista de paths (diretorio ou arquivo).
	 */
	private static final String ITEM_IMAGE = "image";

	/**
	 * Diretorio raiz.
	 */
	private static final String ROOT = "/";

	/**
	 * List of paths to show in favorites list
	 */
	public static final String FAV_PATHS = "FAV_PATHS";

	/**
	 * Parametro de entrada da Activity: filtro de formatos de arquivos. Padrao:
	 * null.
	 */
	public static final String FORMAT_FILTER = "FORMAT_FILTER";

	/**
	 * Parametro de saida da Activity: path escolhido. Padrao: null.
	 */
	public static final String RESULT_PATH = "RESULT_PATH";

	private static final int REQUEST_URL = 1;
	
	private List<String> path = null;
	private TextView myPath;
	private ArrayList<HashMap<String, Object>> mList;

	private InputMethodManager inputManager;
	private String parentPath;
	private String currentPath = ROOT;

	private String[] formatFilter = null;

	private File selectedFile;
	private HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

	private static final String favorites[] = {
        "Download",
        "Downloads",
        "Movies",
        "Videos"
	};
	
	/**
	 * Called when the activity is first created. Configura todos os parametros
	 * de entrada e das VIEWS..
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());

		setContentView(R.layout.file_dialog_main);
		myPath = (TextView) findViewById(R.id.path);

		inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		formatFilter = getIntent().getStringArrayExtra(FORMAT_FILTER);

		String favPaths[] = getIntent().getStringArrayExtra(FAV_PATHS);

		path = new ArrayList<String>();
		mList = new ArrayList<HashMap<String, Object>>();

		// Add shortcuts to favorites
		String storagePath = Environment.getExternalStorageDirectory().getPath();
		for (String fav : favorites) {
		    String p = String.format("%s/%s", storagePath, fav);
		    File file = new File(p);
		    if (file.exists()) {
		        addItem(fav, R.drawable.ic_menu_categories);
		        path.add(p);
		    }
		}

		// Starting path given by originating activity
		if (favPaths != null) {
    		for (String fav : favPaths) {
                File file = new File(fav);
                if (file.exists()) {
                    addItem(fav, R.drawable.ic_menu_categories);
                    path.add(fav);
                }
    		}
		}

        // XXX: Item to allow loading from URL
//        String loadUrl = (String) getText(R.string.loadUrl);
//        addItem(loadUrl, R.drawable.ic_menu_link);
//        path.add(loadUrl);
        
        SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row, new String[] {
                ITEM_KEY, ITEM_IMAGE }, new int[] { R.id.fdrowtext, R.id.fdrowimage });

        setListAdapter(fileList);
	}

	private void getDir(String dirPath) {

		boolean useAutoSelection = dirPath.length() < currentPath.length();

		Integer position = lastPositions.get(parentPath);

		getDirImpl(dirPath);

		if (position != null && useAutoSelection) {
			getListView().setSelection(position);
		}
	}

	/**
	 * Monta a estrutura de arquivos e diretorios filhos do diretorio fornecido.
	 * 
	 * @param dirPath
	 *            Diretorio pai.
	 */
	private void getDirImpl(final String dirPath) {

		currentPath = dirPath;
        path = new ArrayList<String>();
        mList = new ArrayList<HashMap<String, Object>>();

		File f = new File(currentPath);
		File[] files = f.listFiles();
		if (files == null) {
			currentPath = ROOT;
			f = new File(currentPath);
			files = f.listFiles();
		}
		myPath.setText(getText(R.string.location) + ": " + currentPath);

		if (!currentPath.equals(ROOT)) {
			addItem(ROOT, R.drawable.ic_menu_categories);
			path.add(ROOT);

			addItem("../", R.drawable.ic_menu_categories);
			path.add(f.getParent());
			parentPath = f.getParent();
		}

		TreeMap<String, String> dirsMap = new TreeMap<String, String>();
		TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
		TreeMap<String, String> filesMap = new TreeMap<String, String>();
		TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
		for (File file : files) {
			if (file.isDirectory()) {
				String dirName = file.getName();
				dirsMap.put(dirName, dirName);
				dirsPathMap.put(dirName, file.getPath());
			} else {
				final String fileName = file.getName();
				final String fileNameLwr = fileName.toLowerCase();
				// se ha um filtro de formatos, utiliza-o
				if (formatFilter != null) {
					boolean contains = false;
					for (int i = 0; i < formatFilter.length; i++) {
						final String formatLwr = formatFilter[i].toLowerCase();
						if (fileNameLwr.endsWith(formatLwr)) {
							contains = true;
							break;
						}
					}
					if (contains) {
						filesMap.put(fileName, fileName);
						filesPathMap.put(fileName, file.getPath());
					}
					// senao, adiciona todos os arquivos
				} else {
					filesMap.put(fileName, fileName);
					filesPathMap.put(fileName, file.getPath());
				}
			}
		}

		path.addAll(dirsPathMap.tailMap("").values());
		path.addAll(filesPathMap.tailMap("").values());

		SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row, new String[] {
				ITEM_KEY, ITEM_IMAGE }, new int[] { R.id.fdrowtext, R.id.fdrowimage });

		for (String dir : dirsMap.tailMap("").values()) {
			addItem(dir, R.drawable.ic_menu_categories);
		}

		for (String file : filesMap.tailMap("").values()) {
			addItem(file);
		}

		setListAdapter(fileList);
	}

	private void addItem(String fileName, int imageId) {
		HashMap<String, Object> item = new HashMap<String, Object>();
		item.put(ITEM_KEY, fileName);
		if (imageId != -1) {
		    item.put(ITEM_IMAGE, imageId);
		}
		mList.add(item);
	}

    private void addItem(String fileName) {
        addItem(fileName, -1);
    }

	/**
	 * Quando clica no item da lista, deve-se: 1) Se for diretorio, abre seus
	 * arquivos filhos; 2) Se puder escolher diretorio, define-o como sendo o
	 * path escolhido. 3) Se for arquivo, define-o como path escolhido. 4) Ativa
	 * botao de selecao.
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
        if (path.get(position) == getText(R.string.loadUrl)) {
            Intent intent = new Intent(getBaseContext(), UrlDialog.class);
            intent.putExtra(UrlDialog.DEFAULT_URL, getText(R.string.testVideoUrl));
            startActivityForResult(intent, REQUEST_URL);  
        } else {
        	File file = new File(path.get(position));
        
        	setSelectVisible(v);
        
        	if (file.isDirectory()) {
        		if (file.canRead()) {
        			lastPositions.put(currentPath, position);
        			getDir(path.get(position));
        		} else {
        			new AlertDialog.Builder(this).setIcon(R.drawable.icon)
        					.setTitle("[" + file.getName() + "] " + getText(R.string.cant_read_folder))
        					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        
        						@Override
        						public void onClick(DialogInterface dialog, int which) {
        
        						}
        					}).show();
        		}
        	} else {
                selectedFile = file;
                getIntent().putExtra(RESULT_PATH, selectedFile.getPath());
                setResult(RESULT_OK, getIntent());
                finish();
        	}
        }
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			if (!currentPath.equals(ROOT)) {
				getDir(parentPath);
			} else {
				return super.onKeyDown(keyCode, event);
			}
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * Define se o botao de CREATE e visivel.
	 * 
	 * @param v
	 */
	private void setCreateVisible(View v) {
		inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
	}

	/**
	 * Define se o botao de SELECT e visivel.
	 * 
	 * @param v
	 */
	private void setSelectVisible(View v) {
		inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
	}

    @Override
    public synchronized void onActivityResult(final int requestCode,
            int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_URL) {
                getIntent().putExtra(RESULT_PATH, data.getStringExtra(UrlDialog.RESULT_URL));
                setResult(RESULT_OK, getIntent());
                finish();
            }
        }
    }
}
