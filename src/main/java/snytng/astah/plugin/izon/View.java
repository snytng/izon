package snytng.astah.plugin.izon;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import com.change_vision.jude.api.inf.AstahAPI;
import com.change_vision.jude.api.inf.editor.ClassDiagramEditor;
import com.change_vision.jude.api.inf.editor.TransactionManager;
import com.change_vision.jude.api.inf.model.IClassDiagram;
import com.change_vision.jude.api.inf.model.IDiagram;
import com.change_vision.jude.api.inf.presentation.ILinkPresentation;
import com.change_vision.jude.api.inf.presentation.INodePresentation;
import com.change_vision.jude.api.inf.presentation.IPresentation;
import com.change_vision.jude.api.inf.project.ProjectAccessor;
import com.change_vision.jude.api.inf.project.ProjectEvent;
import com.change_vision.jude.api.inf.project.ProjectEventListener;
import com.change_vision.jude.api.inf.ui.IPluginExtraTabView;
import com.change_vision.jude.api.inf.ui.ISelectionListener;
import com.change_vision.jude.api.inf.view.IDiagramEditorSelectionEvent;
import com.change_vision.jude.api.inf.view.IDiagramEditorSelectionListener;
import com.change_vision.jude.api.inf.view.IDiagramViewManager;
import com.change_vision.jude.api.inf.view.IEntitySelectionEvent;
import com.change_vision.jude.api.inf.view.IEntitySelectionListener;

public class View
extends
JPanel
implements
IPluginExtraTabView,
ProjectEventListener,
IEntitySelectionListener,
IDiagramEditorSelectionListener
{
	/**
	 * logger
	 */
	static final Logger logger = Logger.getLogger(View.class.getName());
	static {
		ConsoleHandler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.CONFIG);
		logger.addHandler(consoleHandler);
		logger.setUseParentHandlers(false);
	}

	/**
	 * ??????????????????????????????????????????
	 */
	private static final String VIEW_PROPERTIES = "snytng.astah.plugin.izon.view";

	/**
	 * ????????????????????????
	 */
	private static final ResourceBundle VIEW_BUNDLE = ResourceBundle.getBundle(VIEW_PROPERTIES, Locale.getDefault());

	private String title = "<izon>";
	private String description = "<This plugin shows the depended elements.>";

	private static final long serialVersionUID = 1L;
	private transient ProjectAccessor projectAccessor = null;
	private transient IDiagramViewManager diagramViewManager = null;

	public View() {
		try {
			projectAccessor = AstahAPI.getAstahAPI().getProjectAccessor();
			diagramViewManager = projectAccessor.getViewManager().getDiagramViewManager();
		} catch (Exception e){
			logger.log(Level.WARNING, e.getMessage(), e);
		}

		initProperties();

		initComponents();
	}

	private void initProperties() {
		try {
			title = VIEW_BUNDLE.getString("pluginExtraTabView.title");
			description = VIEW_BUNDLE.getString("pluginExtraTabView.description");
		}catch(Exception e){
			logger.log(Level.WARNING, e.getMessage(), e);
		}
	}

	private void initComponents() {
		// ????????????????????????
		setLayout(new GridLayout(1,1));
		add(createButtonsPane());
	}

	private void addDiagramListeners(){
		diagramViewManager.addDiagramEditorSelectionListener(this);
		diagramViewManager.addEntitySelectionListener(this);
	}

	private void removeDiagramListeners(){
		diagramViewManager.removeDiagramEditorSelectionListener(this);
		diagramViewManager.removeEntitySelectionListener(this);
	}

	// ???????????????
	JLabel  diagramLabel = new JLabel("");
	JButton addButton    = new JButton(VIEW_BUNDLE.getString("Button.Add"));
	JButton insertButton = new JButton(VIEW_BUNDLE.getString("Button.Ins"));
	JButton deleteButton = new JButton(VIEW_BUNDLE.getString("Button.Del"));
	JButton clearButton  = new JButton(VIEW_BUNDLE.getString("Button.Clear"));
	JButton saveButton   = new JButton(VIEW_BUNDLE.getString("Button.Save"));
	JButton loadButton   = new JButton(VIEW_BUNDLE.getString("Button.Load"));
	JButton firstButton  = new JButton("|<");
	JButton prevButton   = new JButton("<");
	JButton numButton    = new JButton("0");
	JButton nextButton   = new JButton(">");
	JButton lastButton   = new JButton(">|");

	// ??????????????????
	@SuppressWarnings("serial")
	private JSeparator getSeparator(){
		return new JSeparator(SwingConstants.VERTICAL){
			@Override public Dimension getPreferredSize() {
				return new Dimension(1, 16);
			}
			@Override public Dimension getMaximumSize() {
				return this.getPreferredSize();
			}
		};
	}

	private void activateButtons(){
		if(izonIndex == 0) {
			addButton.setEnabled(true);
		} else {
			deleteButton.setEnabled(true);
			firstButton.setEnabled(true);
			nextButton.setEnabled(true);
			numButton.setEnabled(true);
			prevButton.setEnabled(true);
			lastButton.setEnabled(true);
		}
	}
	private void deactivateButtons(){
		setButtonsEnabled(false);
	}
	private void setButtonsEnabled(boolean b){
		addButton.setEnabled(b);
		deleteButton.setEnabled(b);
		firstButton.setEnabled(b);
		nextButton.setEnabled(b);
		numButton.setEnabled(b);
		prevButton.setEnabled(b);
		lastButton.setEnabled(b);
	}

	IClassDiagram izonDiagram = null;
	List<List<IPresentation>> izonPresentationList = new ArrayList<>();
	int izonIndex = 0;
	List<INodePresentation> izonCreatedPresentationList = new ArrayList<>();

	private Container createButtonsPane() {

		deactivateButtons();

		//	button mnemonic
		firstButton.setMnemonic(KeyEvent.VK_HOME);
		nextButton.setMnemonic(KeyEvent.VK_RIGHT);
		prevButton.setMnemonic(KeyEvent.VK_LEFT);
		lastButton.setMnemonic(KeyEvent.VK_END);


		// ???????????????????????????
		addButton.addActionListener(e -> {

			try {
				IDiagram currentDiagram = diagramViewManager.getCurrentDiagram();
				if(currentDiagram instanceof IClassDiagram) {
					List<IPresentation> sps = Arrays.stream(diagramViewManager.getSelectedPresentations())
							.filter(p -> p instanceof INodePresentation)
							.collect(Collectors.toList());
					Set<IPresentation> spall = new HashSet<>();

					if(sps.isEmpty()) {
						return;
					}

					do {
						izonPresentationList.add(sps);
						spall.addAll(sps);

						List<IPresentation> tps = new ArrayList<>();
						for(IPresentation p : sps) {
							if(p instanceof INodePresentation) {
								INodePresentation np = (INodePresentation)p;

								ILinkPresentation[] lps = np.getLinks();
								for(ILinkPresentation lp : lps) {
									INodePresentation nps = lp.getSource();
									if(! spall.contains(nps)) {
										spall.add(nps);
										tps.add(nps);
									}
									INodePresentation npt = lp.getTarget();
									if(! spall.contains(npt)) {
										spall.add(npt);
										tps.add(npt);
									}
									if(! spall.contains(lp)) {
										spall.add(lp);
										tps.add(lp);
									}
								}

							} else if(p instanceof ILinkPresentation){
								ILinkPresentation lp = (ILinkPresentation)p;

								INodePresentation nps = lp.getSource();
								if(! spall.contains(nps)) {
									spall.add(nps);
									tps.add(nps);
								}
								INodePresentation npt = lp.getTarget();
								if(! spall.contains(npt)) {
									spall.add(npt);
									tps.add(npt);
								}
							}
						}

						sps = tps;

					} while(!sps.isEmpty());

					int i = 0;
					String diagramName = "izon";
					while(true){
						String dn = "izon" + i;
						if(Arrays.stream(projectAccessor.getProject().getDiagrams())
								.map(IDiagram::getName)
								.noneMatch(n -> n.equals(dn))){
							diagramName = dn;
							break;
						}
						i++;
					}

					TransactionManager.beginTransaction();
					ClassDiagramEditor cde = projectAccessor.getDiagramEditorFactory().getClassDiagramEditor();
					izonDiagram = cde.createClassDiagram(projectAccessor.getProject(), diagramName);
					TransactionManager.endTransaction();
				}

				izonIndex = 1;
				showIzon();
				diagramLabel.setText(currentDiagram.getName());

			}catch(Exception ex){
				TransactionManager.abortTransaction();
				ex.printStackTrace();
			}
		});

		deleteButton.addActionListener(e -> {
			izonDiagram = null;
			izonPresentationList = new ArrayList<>();
			izonIndex = 0;
			izonCreatedPresentationList = new ArrayList<>();
			numButton.setText("0");
			diagramLabel.setText("");
		});

		firstButton.addActionListener(e -> {
			if(! izonPresentationList.isEmpty()) {
				izonIndex = 1;
				showIzon();
			}
		});
		prevButton.addActionListener(e -> {
			if(! izonPresentationList.isEmpty()) {
				izonIndex--;
				if(izonIndex < 1) {
					izonIndex = 1;
				}
				showIzon();
			}
		});
		numButton.addActionListener(e -> {
			showIzon();
		});
		nextButton.addActionListener(e -> {
			if(! izonPresentationList.isEmpty()) {
				izonIndex++;
				if(izonIndex > izonPresentationList.size()) {
					izonIndex = izonPresentationList.size();
				}
				showIzon();
			}
		});
		lastButton.addActionListener(e -> {
			if(! izonPresentationList.isEmpty()) {
				izonIndex = izonPresentationList.size();
				showIzon();
			}
		});


		// ???????????????
		JPanel operationPanel = new JPanel();
		operationPanel.add(diagramLabel);
		operationPanel.add(addButton);
		operationPanel.add(deleteButton);
		operationPanel.add(getSeparator());// ??????????????????
		operationPanel.add(firstButton);
		operationPanel.add(prevButton);
		operationPanel.add(numButton);
		operationPanel.add(nextButton);
		operationPanel.add(lastButton);


		// ???????????????
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(operationPanel);

		return panel;
	}

	static List<String> linkPresentationTypes = new ArrayList<>();
	static {
		linkPresentationTypes.add("Association");
		linkPresentationTypes.add("Generalization");
		linkPresentationTypes.add("Realization");
		linkPresentationTypes.add("Dependency");
		linkPresentationTypes.add("Usage");
	}

	private void showIzon() {
		if(izonDiagram == null) {
			return;
		}

		numButton.setText(izonIndex + "/" + izonPresentationList.size());

		List<INodePresentation> nps = izonPresentationList.stream()
				.limit(izonIndex)
				.flatMap(List<IPresentation>::stream)
				.filter(INodePresentation.class::isInstance)
				.map(INodePresentation.class::cast)
				.collect(Collectors.toList());

		List<ILinkPresentation> lps = izonPresentationList.stream()
				.limit(izonIndex)
				.flatMap(List<IPresentation>::stream)
				.filter(ILinkPresentation.class::isInstance)
				.map(ILinkPresentation.class::cast)
				.collect(Collectors.toList());

		try {
			TransactionManager.beginTransaction();
			ClassDiagramEditor cde = projectAccessor.getDiagramEditorFactory().getClassDiagramEditor();
			cde.setDiagram(izonDiagram);

			for(IPresentation p : izonCreatedPresentationList) {
				cde.deletePresentation(p);
			}

			izonCreatedPresentationList = new ArrayList<>();
			for(INodePresentation np : nps) {
				INodePresentation nnp = null;
				if(np.getType().equals("Class")) {
					nnp = cde.createNodePresentation(np.getModel(), np.getLocation());
					//nnp.setHeight(np.getHeight());
					//nnp.setWidth(np.getWidth());
					izonCreatedPresentationList.add(nnp);
				} else if (np.getType().equals("Note")) {
					nnp = cde.createNote(np.getLabel(), np.getLocation());
					//nnp.setHeight(np.getHeight());
					//nnp.setWidth(np.getWidth());
					izonCreatedPresentationList.add(nnp);
				} else {
					System.out.println("np type=" + np.getType());
				}
			}

			for(ILinkPresentation lp : lps) {
				Optional<INodePresentation> source = izonCreatedPresentationList.stream()
						.filter(x -> x.getLocation().equals(lp.getSource().getLocation()))
						.findFirst();
				Optional<INodePresentation> target = izonCreatedPresentationList.stream()
						.filter(x -> x.getLocation().equals(lp.getTarget().getLocation()))
						.findFirst();
				if(source.isPresent() && target.isPresent()) {
					if(linkPresentationTypes.contains(lp.getType())) {
						ILinkPresentation nlp = cde.createLinkPresentation(lp.getModel(), source.get(), target.get());
						//nlp.setAllPoints(lp.getAllPoints());
					} else if(lp.getType().equals("NoteAnchor")) {
						ILinkPresentation nlp = null;
						if(source.get().getType().equals("Note")) {
							nlp = cde.createNoteAnchor(source.get(), target.get());
						} else {
							nlp = cde.createNoteAnchor(target.get(), source.get());
						}
						//nlp.setAllPoints(lp.getAllPoints());
					} else {
						System.out.println("lp type=" + lp.getType());
					}
				}
			}
			TransactionManager.endTransaction();
		}catch(Exception e){
			TransactionManager.abortTransaction();
			logger.log(Level.WARNING, e.getMessage(), e);
		}

		diagramViewManager.open(izonDiagram);
		diagramViewManager.unselectAll();
	}

	/**
	 * ????????????????????????????????????????????????????????????
	 */
	@Override
	public void projectChanged(ProjectEvent e) {
		updateDiagramView();
	}
	@Override
	public void projectClosed(ProjectEvent e) {
		// Do nothing when project is closed
	}

	@Override
	public void projectOpened(ProjectEvent e) {
		// Do nothing when project is opened
	}

	/**
	 * ??????????????????????????????????????????????????????
	 */
	@Override
	public void diagramSelectionChanged(IDiagramEditorSelectionEvent e) {
		updateDiagramView();
	}

	/**
	 * ?????????????????????????????????????????????????????????
	 */
	@Override
	public void entitySelectionChanged(IEntitySelectionEvent e) {
		updateDiagramView();
	}

	/**
	 * ?????????????????????
	 */
	private void updateDiagramView() {
		deactivateButtons();
		activateButtons();
		logger.log(Level.INFO, "update diagram view.");
	}

	// IPluginExtraTabView
	@Override
	public void addSelectionListener(ISelectionListener listener) {
		// Do nothing
	}

	@Override
	public void activated() {
		// ????????????????????????
		addDiagramListeners();
	}

	@Override
	public void deactivated() {
		// ????????????????????????
		removeDiagramListeners();
	}

	@Override
	public Component getComponent() {
		return this;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getTitle() {
		return title;
	}

}
