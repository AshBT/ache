/*
############################################################################
##
## Copyright (C) 2006-2009 University of Utah. All rights reserved.
##
## This file is part of DeepPeep.
##
## This file may be used under the terms of the GNU General Public
## License version 2.0 as published by the Free Software Foundation
## and appearing in the file LICENSE.GPL included in the packaging of
## this file.  Please review the following to ensure GNU General Public
## Licensing requirements will be met:
## http://www.opensource.org/licenses/gpl-license.php
##
## If you are unsure which license is appropriate for your use (for
## instance, you are interested in developing a commercial derivative
## of DeepPeep), please contact us at deeppeep@sci.utah.edu.
##
## This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
## WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
##
############################################################################
*/
package focusedCrawler.link;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Vector;

import focusedCrawler.link.backlink.BacklinkSurfer;
import focusedCrawler.link.classifier.LinkClassifier;
import focusedCrawler.link.classifier.LinkClassifierException;
import focusedCrawler.link.frontier.FrontierManager;
import focusedCrawler.link.frontier.FrontierPersistentException;
import focusedCrawler.link.frontier.LinkRelevance;
import focusedCrawler.target.model.Page;
import focusedCrawler.util.parser.BackLinkNeighborhood;
import focusedCrawler.util.parser.LinkNeighborhood;

/**
 * This class is responsible to manage the info in the graph (backlinks and outlinks).
 * @author lbarbosa
 *
 */

public class BipartiteGraphManager {

	private FrontierManager frontierManager;
	private BacklinkSurfer surfer;
	private LinkClassifier backlinkClassifier;
	private LinkClassifier outlinkClassifier;
	private BipartiteGraphRepository graphRepository;
	
    // Data structure for stop conditions //////////////////////////
    private int maxPagesPerDomain = 100; // Maximum number of pages per each domain
    private HashMap<String, Integer> domainCounter;// Count number of pages for each domain
    ///////////////////////////////////////////////////////////////
	
	public BipartiteGraphManager(FrontierManager frontierManager,
	                             BipartiteGraphRepository graphRepository,
	                             LinkClassifier outlinkClassifier,
	                             int maxPagesPerDomain,
	                             BacklinkSurfer surfer,
	                             LinkClassifier backlinkClassifier) {
        this.frontierManager = frontierManager;
        this.graphRepository = graphRepository;
        this.outlinkClassifier = outlinkClassifier;
        this.backlinkClassifier = backlinkClassifier;
        this.domainCounter = new HashMap<String, Integer>();
        this.maxPagesPerDomain = maxPagesPerDomain;
        this.surfer = surfer;
    }

	public void setBacklinkClassifier(LinkClassifier classifier){
		this.backlinkClassifier = classifier;
	}

	public void setOutlinkClassifier(LinkClassifier classifier){
		this.outlinkClassifier = classifier;
	}

	public BipartiteGraphRepository getRepository(){
		return this.graphRepository;
	}
	
    public void insertOutlinks(Page page) throws IOException, FrontierPersistentException, LinkClassifierException {
    	
        LinkRelevance[] linksRelevance = outlinkClassifier.classify(page);
        
        ArrayList<LinkRelevance> temp = new ArrayList<LinkRelevance>();
        HashSet<String> relevantURLs = new HashSet<String>();
                
        for (int i = 0; i < linksRelevance.length; i++) {
        	//System.out.println("linksRelevance.length "+linksRelevance.length);
            if (frontierManager.isRelevant(linksRelevance[i])) {
                            	
                String url = linksRelevance[i].getURL().toString();
                //System.out.println(url);
                if (!relevantURLs.contains(url)) {
                    
                    String domain = linksRelevance[i].getTopLevelDomainName();
                    
                    Integer domainCount;
                    synchronized (domainCounter) {
                        domainCount = domainCounter.get(domain);
                        if (domainCount == null) {
                            domainCount = 0;
                        } else {
                            domainCount++;
                        }
                        domainCounter.put(domain, domainCount);
                    }
                    
                    if (domainCount < maxPagesPerDomain) { // Stop Condition
                        relevantURLs.add(url);
                        temp.add(linksRelevance[i]);
                    }
                    
                }
            }
        }

        LinkRelevance[] filteredLinksRelevance = temp.toArray(new LinkRelevance[relevantURLs.size()]);
        
        LinkNeighborhood[] lns = page.getParsedData().getLinkNeighborhood();
        for (int i = 0; i < lns.length; i++) {
            if (!relevantURLs.contains(lns[i].getLink().toString())) {
                lns[i] = null;
            }
        }
        
        graphRepository.insertOutlinks(page.getURL(), lns);
        frontierManager.insert(filteredLinksRelevance);
    }
	
	public void insertBacklinks(Page page) throws IOException, FrontierPersistentException, LinkClassifierException{
		URL url = page.getURL();
		BackLinkNeighborhood[] links = graphRepository.getBacklinks(url);
		if(links == null || (links != null && links.length < 10)){
			links = surfer.getLNBacklinks(url);	
		}
		if(links != null && links.length > 0){
			LinkRelevance[] linksRelevance = new LinkRelevance[links.length];
			for (int i = 0; i < links.length; i++){
				BackLinkNeighborhood backlink = links[i];
				if(backlink != null){
					LinkNeighborhood ln = new LinkNeighborhood(new URL(backlink.getLink()));
					String title = backlink.getTitle();
					if(title != null){
						ln.setAround(tokenizeText(title));
					}
					linksRelevance[i] = backlinkClassifier.classify(ln);
				}
			}
			frontierManager.insert(linksRelevance);
		}
		URL normalizedURL = new URL(url.getProtocol(), url.getHost(), "/"); 
		graphRepository.insertBacklinks(normalizedURL, links);
	}

	private String[] tokenizeText(String text) {
		StringTokenizer tokenizer = new StringTokenizer(text," ");
		Vector<String> anchorTemp = new Vector<String>();
		while(tokenizer.hasMoreTokens()){
			 anchorTemp.add(tokenizer.nextToken());
		}
		String[] aroundArray = new String[anchorTemp.size()];
		anchorTemp.toArray(aroundArray);
		return aroundArray;
	}

}
